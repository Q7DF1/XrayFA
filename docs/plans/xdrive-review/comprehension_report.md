# Comprehension review

Status: completed, fresh-context reviewer. Required persona and target reads
confirmed. No other context supplied. The following is the complete reviewer
result; dispositions are recorded in the consolidated report.

## What this proposes

Add a shared form that creates and edits VLESS XDRIVE configurations using Google Drive, S3, template, or local storage, saving them as existing full-JSON configuration records. Validate storage settings, require VLESS encryption and empty flow, generate compliant Mux settings for new profiles, and preserve imported configuration data without silently repairing invalid settings. Unsupported document shapes remain in the raw JSON editor, while supported shapes receive structured editing that preserves unknown fields. Prohibit sharing and export through UI, QR, clipboard, and platform boundaries, then verify configuration loading, actual local-storage traffic through Mux, editor behavior, and sharing restrictions.

## How the existing system works, per this document

XrayFA shares ordinary-node editing and protocol parsing between Android and iOS; ordinary nodes are stored as protocol links that produce outbounds for previews, delay tests, and connections. A separate full-JSON editor preserves arbitrary Xray settings, and its connection preparation supplies the platform VPN inlet and DNS. The pinned native core supports XDRIVE, but ordinary-node parsing rejects it, the ordinary editor lacks its controls, and ordinary VLESS outbound generation disables Mux. Existing JSON records already exclude ordinary sharing and lack application-managed subscription chains and ordinary-node delay probing.

## Findings

1. **Severity: Major**
   **Affected Scope:** Verified upstream contract and Phase 1: Domain configuration builder; template and local backends.
   **Discrepancy Description & Rationale:** The document promises structured configuration and validation for all four backends, but only Google Drive and S3 receive usable configuration contracts. Template and local are described as having backend-specific behavior, without identifying their required settings, how the form represents them, or what makes their settings valid. A reader can explain the Google Drive and S3 setup but cannot explain how to create a valid template or local profile from this document. Define their required inputs and validation, or explicitly identify the advanced-JSON workflow and link its contract.

2. **Severity: Major**
   **Affected Scope:** Phase 1: Domain configuration builder and Phase 2: Shared local editor and sharing guards; invalid imported Mux settings.
   **Discrepancy Description & Rationale:** Supported imported profiles open in structured fields, imported invalid Mux settings must remain validation errors, and saving requires validation. However, the form-state inventory contains no Mux controls, and the document does not explain how a user switches such a profile to raw editing or otherwise corrects those errors. The intended recovery workflow is therefore unclear: the reader cannot determine how a supported but invalid imported profile becomes saveable without automatic repair. State the correction path explicitly.

Reads completed: the comprehension reviewer instructions and target document lines 1–255, including FeatureRange 1–149 and DetailedRange 150–255. No code, external sources, or other documents were consulted. No edits were made.
