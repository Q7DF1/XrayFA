// Run with go run scripts/diagnostics/xdrive-storage-latency.go -config /private/server.json.
// The config stays local. Only unique diagnostic objects created here are deleted.
package main

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptrace"
	"net/url"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

type operation struct {
	Method  string
	URL     string
	Headers map[string]string
}
type template struct {
	Auth                   struct{ Type, Username, Password string }
	Put, Get, Delete, List operation
}
type config struct {
	Inbounds []struct {
		StreamSettings struct {
			X struct {
				Service string
				S3      struct {
					Endpoint, Region, Bucket string
					PathStyle                *bool
				}
				RemoteFolder string
				Secrets      []string
				Template     json.RawMessage
			} `json:"xdriveSettings"`
		}
	}
}
type result struct {
	Label   string  `json:"label"`
	Op      string  `json:"op"`
	Round   int     `json:"round"`
	Size    int     `json:"size"`
	Status  int     `json:"status"`
	Bytes   int     `json:"bytes"`
	Match   *bool   `json:"match,omitempty"`
	Reused  bool    `json:"reused"`
	Proto   string  `json:"proto"`
	TCP     float64 `json:"tcp_ms"`
	TLS     float64 `json:"tls_ms"`
	GotConn float64 `json:"got_conn_ms"`
	Wrote   float64 `json:"wrote_ms"`
	First   float64 `json:"first_byte_ms"`
	Headers float64 `json:"headers_ms"`
	Total   float64 `json:"total_ms"`
	Error   string  `json:"error,omitempty"`
}

func main() {
	cfgPath := flag.String("config", "", "private Xray server config")
	label := flag.String("label", "host", "measurement location")
	pin := flag.String("ip", "", "optional IPv4 pin for the GET service hostname, preserving Host/SNI")
	roots := flag.String("roots", "", "optional PEM root CA bundle (Android standalone helper)")
	rounds := flag.Int("rounds", 2, "repetitions per size")
	sizeList := flag.String("sizes", "1024,16384,262144,1048576", "comma-separated payload sizes in bytes, each at most 8 MiB")
	flag.Parse()
	if *rounds < 1 || *rounds > 10 {
		panic("rounds must be between 1 and 10")
	}
	var sizes []int
	for _, item := range strings.Split(*sizeList, ",") {
		n, e := strconv.Atoi(strings.TrimSpace(item))
		if e != nil || n <= 0 || n > 8<<20 {
			panic("invalid payload size")
		}
		sizes = append(sizes, n)
	}
	b, err := os.ReadFile(*cfgPath)
	must(err)
	var c config
	must(json.Unmarshal(b, &c))
	if len(c.Inbounds) == 0 {
		panic("no inbound")
	}
	x := c.Inbounds[0].StreamSettings.X
	raw := x.Template
	if len(raw) == 0 && x.Service != "s3" {
		panic("no template")
	}
	if len(raw) > 0 && raw[0] == '"' {
		var str string
		must(json.Unmarshal(raw, &str))
		raw = []byte(str)
	}
	var tmpl template
	if x.Service == "s3" {
		if len(x.Secrets) < 2 {
			panic("S3 needs access key and secret key")
		}
		u, e := url.Parse(x.S3.Endpoint)
		must(e)
		if u.Scheme != "https" || u.Hostname() == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" {
			panic("invalid HTTPS S3 endpoint")
		}
		if x.S3.PathStyle == nil || *x.S3.PathStyle {
			u.Path = strings.TrimRight(u.Path, "/") + "/" + x.S3.Bucket
		} else {
			u.Host = x.S3.Bucket + "." + u.Host
		}
		u.Path = strings.TrimRight(u.Path, "/") + "/"
		base := u.String()
		object := base + strings.Trim(x.RemoteFolder, "/") + "/{name}"
		tmpl.Put = operation{Method: "PUT", URL: object}
		tmpl.Get = operation{Method: "GET", URL: object}
		tmpl.Delete = operation{Method: "DELETE", URL: object}
		q := url.Values{"list-type": {"2"}, "prefix": {strings.Trim(x.RemoteFolder, "/") + "/diagnostic-latency-"}}
		tmpl.List = operation{Method: "GET", URL: base + "?" + q.Encode()}
	} else {
		must(json.Unmarshal(raw, &tmpl))
	}
	if x.Service != "s3" && tmpl.Auth.Type != "basic" {
		panic("this diagnostic supports basic auth templates only")
	}
	vars := map[string]string{"folder": x.RemoteFolder, "prefix": "diagnostic-latency"}
	for i, v := range x.Secrets {
		vars[fmt.Sprint("secret", i)] = v
	}
	subst := func(s string) string {
		for k, v := range vars {
			s = strings.ReplaceAll(s, "{"+k+"}", v)
		}
		return s
	}
	tlsConfig := &tls.Config{MinVersion: tls.VersionTLS12}
	if *roots != "" {
		pem, e := os.ReadFile(*roots)
		must(e)
		pool := x509.NewCertPool()
		if !pool.AppendCertsFromPEM(pem) {
			panic("invalid roots")
		}
		tlsConfig.RootCAs = pool
	}
	serviceURL, err := url.Parse(tmpl.Get.URL)
	must(err)
	tr := &http.Transport{TLSClientConfig: tlsConfig, ForceAttemptHTTP2: true}
	if *pin != "" {
		tr.DialContext = func(ctx context.Context, network, addr string) (net.Conn, error) {
			host, port, e := net.SplitHostPort(addr)
			if e != nil {
				return nil, e
			}
			if host == serviceURL.Hostname() {
				addr = net.JoinHostPort(*pin, port)
			}
			return (&net.Dialer{Timeout: 20 * time.Second}).DialContext(ctx, network, addr)
		}
	}
	client := &http.Client{Transport: tr, Timeout: 90 * time.Second}
	defer tr.CloseIdleConnections()
	enc := json.NewEncoder(os.Stdout)
	// Track only the unique objects uploaded by this run. B2 keeps historical versions.
	versions := make(map[string]string)
	perform := func(opName string, o operation, name string, body []byte, expected []byte, round, size int) result {
		vars["name"] = name
		start := time.Now()
		r := result{Label: *label, Op: opName, Round: round, Size: size}
		var mu sync.Mutex
		var tcpStart, tlsStart time.Time
		ms := func() float64 { return float64(time.Since(start).Microseconds()) / 1000 }
		trace := &httptrace.ClientTrace{ConnectStart: func(_, _ string) { mu.Lock(); tcpStart = time.Now(); mu.Unlock() }, ConnectDone: func(_, _ string, _ error) {
			mu.Lock()
			r.TCP = float64(time.Since(tcpStart).Microseconds()) / 1000
			mu.Unlock()
		}, TLSHandshakeStart: func() { mu.Lock(); tlsStart = time.Now(); mu.Unlock() }, TLSHandshakeDone: func(_ tls.ConnectionState, _ error) {
			mu.Lock()
			r.TLS = float64(time.Since(tlsStart).Microseconds()) / 1000
			mu.Unlock()
		}, GotConn: func(i httptrace.GotConnInfo) { mu.Lock(); r.Reused = i.Reused; r.GotConn = ms(); mu.Unlock() }, WroteRequest: func(httptrace.WroteRequestInfo) { mu.Lock(); r.Wrote = ms(); mu.Unlock() }, GotFirstResponseByte: func() { mu.Lock(); r.First = ms(); mu.Unlock() }}
		req, e := http.NewRequest(o.Method, subst(o.URL), bytes.NewReader(body))
		if e != nil {
			panic("invalid operation URL")
		}
		req = req.WithContext(httptrace.WithClientTrace(req.Context(), trace))
		if x.Service != "s3" {
			req.SetBasicAuth(subst(tmpl.Auth.Username), subst(tmpl.Auth.Password))
		}
		req.Header.Set("Cache-Control", "no-cache")
		req.Header.Set("User-Agent", "Mozilla/5.0 XrayFA-storage-diagnostic")
		for k, v := range o.Headers {
			req.Header.Set(k, subst(v))
		}
		if x.Service == "s3" {
			if opName == "DELETE" && versions[name] != "" {
				q := req.URL.Query()
				q.Set("versionId", versions[name])
				req.URL.RawQuery = q.Encode()
			}
			signS3(req, body, x.S3.Region, x.Secrets)
		}
		resp, e := client.Do(req)
		mu.Lock()
		r.Headers = ms()
		mu.Unlock()
		if e == nil {
			r.Status = resp.StatusCode
			if x.Service == "s3" && opName == "PUT" && r.Status >= 200 && r.Status < 300 {
				versions[name] = resp.Header.Get("X-Amz-Version-Id")
			}
			r.Proto = resp.Proto
			data, readErr := io.ReadAll(io.LimitReader(resp.Body, 32<<20))
			resp.Body.Close()
			r.Bytes = len(data)
			if expected != nil {
				ok := len(data) == len(expected) && sha256.Sum256(data) == sha256.Sum256(expected)
				r.Match = &ok
			}
			e = readErr
		}
		mu.Lock()
		r.Total = ms()
		if e != nil {
			if u, ok := e.(*url.Error); ok {
				e = u.Err
			}
			r.Error = e.Error()
		}
		snapshot := r
		mu.Unlock()
		must(enc.Encode(snapshot))
		return snapshot
	}
	token := make([]byte, 8)
	_, err = rand.Read(token)
	must(err)
	prefix := "diagnostic-latency-" + hex.EncodeToString(token)
	for i := 0; i < 3; i++ {
		tr.CloseIdleConnections()
		perform("GET-missing-cold", tmpl.Get, prefix+"-missing.seg", nil, nil, i, 0)
	}
	for i := 0; i < *rounds; i++ {
		perform("LIST", tmpl.List, "", nil, nil, i, 0)
	}
	for _, size := range sizes {
		for round := 0; round < *rounds; round++ {
			data := make([]byte, size)
			_, err = rand.Read(data)
			must(err)
			name := fmt.Sprintf("%s-%d-%d.seg", prefix, size, round)
			put := perform("PUT", tmpl.Put, name, data, nil, round, size)
			if put.Status >= 200 && put.Status < 300 {
				perform("GET", tmpl.Get, name, nil, data, round, size)
			}
			perform("DELETE", tmpl.Delete, name, nil, nil, round, size)
		}
	}
}
func must(err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

// signS3 uses the same SigV4 request shape as the XDRIVE S3 transport.
func signS3(req *http.Request, body []byte, region string, secrets []string) {
	hash := func(b []byte) string { h := sha256.Sum256(b); return hex.EncodeToString(h[:]) }
	mac := func(k []byte, v string) []byte { h := hmac.New(sha256.New, k); h.Write([]byte(v)); return h.Sum(nil) }
	now := time.Now().UTC()
	stamp, date := now.Format("20060102T150405Z"), now.Format("20060102")
	payload := hash(body)
	req.Header.Set("X-Amz-Date", stamp)
	req.Header.Set("X-Amz-Content-Sha256", payload)
	headers := "host:" + req.URL.Host + "\nx-amz-content-sha256:" + payload + "\nx-amz-date:" + stamp + "\n"
	signed := "host;x-amz-content-sha256;x-amz-date"
	if len(secrets) > 2 && secrets[2] != "" {
		req.Header.Set("X-Amz-Security-Token", secrets[2])
		headers += "x-amz-security-token:" + strings.Join(strings.Fields(secrets[2]), " ") + "\n"
		signed += ";x-amz-security-token"
	}
	if region == "" {
		region = "us-east-1"
	}
	req.URL.RawQuery = strings.ReplaceAll(req.URL.Query().Encode(), "+", "%20")
	canonical := req.Method + "\n" + req.URL.EscapedPath() + "\n" + req.URL.RawQuery + "\n" + headers + "\n" + signed + "\n" + payload
	scope := date + "/" + region + "/s3/aws4_request"
	str := "AWS4-HMAC-SHA256\n" + stamp + "\n" + scope + "\n" + hash([]byte(canonical))
	key := mac([]byte("AWS4"+secrets[1]), date)
	key = mac(key, region)
	key = mac(key, "s3")
	key = mac(key, "aws4_request")
	req.Header.Set("Authorization", "AWS4-HMAC-SHA256 Credential="+secrets[0]+"/"+scope+", SignedHeaders="+signed+", Signature="+hex.EncodeToString(mac(key, str)))
}
