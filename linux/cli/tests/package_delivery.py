"""Archive validation evidence without dereferencing failure-injection links/devices."""
import argparse,hashlib,json,os,platform,re,shutil,stat,subprocess,tarfile,tempfile,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--matrix',type=Path,required=True);p.add_argument('--acceptance',type=Path,required=True);p.add_argument('--delivery',type=Path,default=Path('linux/cli/build/delivery'));a=p.parse_args()
root=Path(__file__).resolve().parents[3];delivery=a.delivery.resolve();os.umask(0o077);delivery.mkdir(exist_ok=True,parents=True);delivery.chmod(0o700)
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def command(args):return subprocess.check_output(args,text=True).strip()
def clone(source,target):
    target.mkdir(parents=True,exist_ok=True)
    for src in source.iterdir():
        dst=target/src.name
        if src.is_symlink():
            if dst.is_symlink():dst.unlink()
            dst.with_name(dst.name+'.symlink.txt').write_text('Failure-injection symlink; not dereferenced in delivery.\nTarget: '+os.readlink(src)+'\n')
        elif src.is_dir():clone(src,dst)
        elif stat.S_ISREG(src.stat().st_mode):
            if dst.is_symlink():dst.unlink()
            shutil.copy2(src,dst)
        else:dst.with_name(dst.name+'.special-file.txt').write_text('Special fixture omitted; never read as regular data.\n')
cli_hash=digest(delivery/'xrayfa')
coverage=json.loads((a.matrix/'coverage.json').read_text());acceptance=json.loads((a.acceptance/'acceptance.json').read_text())
assert coverage['cliSha256']==acceptance['cliSha256']==cli_hash
assert coverage['summary'].get('failed',0)==0 and coverage['unknown']==coverage['unexecuted']==0
assert acceptance['failed']==0
assert coverage['manifestSha256']==digest(root/'linux/cli/tests/manifest-full.json')
clone(a.matrix,delivery/'matrix');clone(a.acceptance,delivery/'acceptance')
art=delivery/'evidence';art.mkdir(exist_ok=True)
for pattern in ('xrayfa-linux-*.log','xrayfa-linux-elf-*.txt'):
    for src in Path('/tmp').glob(pattern):shutil.copy2(src,art/src.name)
for source,name in [(Path('/tmp/xrayfa-linux-matrix-01'),'matrix-01'),(Path('/tmp/xrayfa-linux-matrix-02'),'matrix-02'),(Path('/tmp/xrayfa-linux-acceptance-01'),'acceptance-01')]:
    if source.exists():clone(source,delivery/'history'/name)
for name in ('manifest.json','manifest.sha256','manifest-full.json','manifest-full.sha256'):shutil.copy2(root/'linux/cli/tests'/name,delivery/name)
for module in ('common','domain'):
    for suite in ('linuxX64Test','testDebugUnitTest'):clone(root/module/'build/test-results'/suite,art/module/suite)
dep_log=Path('/tmp/xrayfa-linux-final-build2.log')
text=dep_log.read_text() if dep_log.exists() else command(['./gradlew','-PenableLinuxCli=true',':linux:cli:dependencies','--console=plain'])
start=text.index('linuxX64CompileKlibraries - ');end=text.index('\n\n',start)
(delivery/'resolved-dependencies.txt').write_text(text[start:end]+'\n')
props=Path.home()/'.konan/kotlin-native-prebuilt-linux-x86_64-2.4.10/konan/konan.properties'
shutil.copy2(props,art/'konan-2.4.10.properties')
def versions(path):
    v=set(re.findall(r'GLIBC_(\d+(?:\.\d+)+)',command(['readelf','--version-info',str(path)])))
    return sorted(v,key=lambda x:tuple(map(int,x.split('.'))))
counts={}
for module in ('common','domain'):
    for suite in ('linuxX64Test','testDebugUnitTest'):
        files=list((root/module/'build/test-results'/suite).glob('*.xml'))
        counts[module+'/'+suite]={k:sum(int(ET.parse(p).getroot().get(k,0)) for p in files) for k in ('tests','failures','errors','skipped')}
        assert counts[module+'/'+suite]['failures']==counts[module+'/'+suite]['errors']==0
default_log=Path('/tmp/xrayfa-linux-default-projects.log')
assert 'linux:cli' not in (default_log.read_text() if default_log.exists() else command(['./gradlew','projects','--console=plain']))
assert not command(['git','diff','--name-only','--','common/src/commonMain','common/src/androidMain','common/src/iosMain','domain/src/commonMain','domain/src/androidMain','domain/src/iosMain'])
meta={'utc':command(['date','-u','+%FT%TZ']),'baselineCommit':command(['git','rev-parse','HEAD']),'system':platform.platform(),'osRelease':Path('/etc/os-release').read_text(),'glibc':command(['getconf','GNU_LIBC_VERSION']),'architecture':platform.machine(),'cliSha256':cli_hash,'cliSize':(delivery/'xrayfa').stat().st_size,'cliGlibcSymbolVersions':versions(delivery/'xrayfa'),'dynamicCrypto':{'path':'/usr/lib/x86_64-linux-gnu/libcrypto.so.3','package':command(['dpkg-query','-W','libssl3']),'glibcSymbolVersions':versions(Path('/usr/lib/x86_64-linux-gnu/libcrypto.so.3'))},'coreSha256':digest(Path('/usr/local/bin/xray')),'coreVersion':command(['/usr/local/bin/xray','version']),'projectGoRequirement':next(x.strip() for x in (root/'AndroidLibXrayLite/go.mod').read_text().splitlines() if 'github.com/xtls/xray-core' in x),'tests':counts,'androidCompile':'passed with default Linux flag disabled; see mobile-regression log','iosExecution':'not run on Linux; no commonMain/androidMain/iosMain source or iOS target declarations changed','toolchain':{'kotlin':'2.4.10','gradle':'9.3.1','agp':'9.1.1','nativeSysroot':'x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2'},'dependencies':{'coroutines':'1.9.0','serialization':'1.7.3','koin':'4.0.1','stately':'2.1.0','atomicfuResolved':'0.25.0'},'matrix':coverage['summary'],'negativeStages':{'add':sum(r['expected']=='reject' for r in coverage['results']),'core':sum(r['expected']=='core-reject' for r in coverage['results'])},'acceptance':{'passed':acceptance['passed'],'failed':acceptance['failed']},'recordedPathMapping':{str(a.matrix.resolve()):'matrix',str(a.acceptance.resolve()):'acceptance'},'certifiedScope':'Ubuntu 22.04.5 LTS x86-64 configuration generation and external Xray -test only','unverified':['real TUN/proxy and networking','remote credentials and reachability','Ubuntu 18.04 and other distributions','iOS runtime/build on macOS']}
(delivery/'environment.json').write_text(json.dumps(meta,indent=2)+'\n')
with tempfile.TemporaryDirectory(prefix='xrayfa-manifest-reproduction-') as temp:
    temp=Path(temp)
    for name in ('freeze_manifest.py','expand_manifest.py'):
        shutil.copy2(root/'linux/cli/tests'/name,temp/name)
        subprocess.run(['python3',str(temp/name)],check=True,capture_output=True)
    for name in ('manifest.json','manifest-full.json'):
        assert (temp/name).read_bytes()==(root/'linux/cli/tests'/name).read_bytes()
    (delivery/'manifest-reproduction.json').write_text(json.dumps({'byteForByteReproduction':True,'baseSha256':digest(temp/'manifest.json'),'fullSha256':digest(temp/'manifest-full.json')},indent=2)+'\n')
shutil.copy2(root/'linux/cli/README.md',delivery/'README.md')
shutil.copy2(root/'LICENSE',delivery/'LICENSE')
# No active symlinks or special files are shipped, even from test fixture directories.
assert not any(x.is_symlink() for x in delivery.rglob('*'))
checks=[]
for f in sorted(delivery.rglob('*')):
    if f.is_file() and f.name!='SHA256SUMS':checks.append(digest(f)+'  '+str(f.relative_to(delivery)))
(delivery/'SHA256SUMS').write_text('\n'.join(checks)+'\n')
archive=delivery.parent/'xrayfa-linux-x64-config.tar.gz'
with tarfile.open(archive,'w:gz',compresslevel=6) as t:t.add(delivery,arcname='xrayfa-linux-x64-config',recursive=True)
archive.with_suffix(archive.suffix+'.sha256').write_text(digest(archive)+'  '+archive.name+'\n')
print(json.dumps({'cliSha256':cli_hash,'matrix':coverage['summary'],'acceptance':meta['acceptance'],'cliMaxGlibc':meta['cliGlibcSymbolVersions'][-1],'cryptoMaxGlibc':meta['dynamicCrypto']['glibcSymbolVersions'][-1],'archive':str(archive),'archiveSize':archive.stat().st_size},indent=2))
