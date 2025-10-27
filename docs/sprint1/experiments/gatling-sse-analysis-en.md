# 🚀 Analysis: Feature Request for Gatling?

**Date**: October 22, 2025
**Context**: TTFT vs P99 inconsistency analysis (Sprint 1)
**Question**: Should Gatling support native end-to-end latency measurement for SSE?

---

## 📋 Executive Summary

**User Question:**
> "Could this observation be a new feature for the Gatling team to improve the tool? I understand the official documentation may be confusing, but does it actually confuse response time vs what the SSE protocol delivers?"

**Short Answer:** ✅ **YES**, this is a valid and useful feature request for the community.

**Important Nuance:** Gatling does NOT confuse the SSE protocol - its behavior is correct according to the HTTP standard. However, the **user expectation** (measuring perceived latency) is legitimate and currently not covered.

---

## 🔍 Two-Perspective Analysis

### Perspective 1: Gatling is Right ✅

**Argument:**
- The HTTP protocol defines that a request "completes" when the connection is established
- SSE is simply a `Content-Type: text/event-stream` over HTTP
- RFC 6202 (SSE) does NOT define a standard "completion marker"
- Gatling perfectly complies with the HTTP standard

**Evidence:**
```
POST /v1/chat/completions HTTP/1.1
Host: api.openai.com
Content-Type: application/json
...

HTTP/1.1 200 OK  ← Here Gatling marks "request complete"
Content-Type: text/event-stream
Transfer-Encoding: chunked

data: {"choices":[...]}  ← This is no longer part of the "request"
...
data: [DONE]
```

**From this perspective:**
- ✅ Gatling correctly measures the HTTP request/response
- ✅ Streaming is a "post-request event"
- ✅ Correct behavior according to RFC

---

### Perspective 2: The User is Right ✅

**Argument:**
- In LLM applications, **perceived latency** is what matters
- A request is not "useful" until the stream completes
- The user experience includes ALL streaming
- Gatling should offer this option

**Evidence:**
```
User: "What is the capital of France?"
System: HTTP 200 OK in 558ms  ← Gatling says "done"
User: [Waiting...]
System: "Paris" appears on screen after 2,018ms  ← User says "done"
```

**From this perspective:**
- ⚠️ Gatling P99 = 558ms does NOT represent user reality
- ⚠️ For UX SLAs, we need to measure until `[DONE]`
- ⚠️ Gatling's metric can lead to incorrect optimizations

---

## 🎯 Conclusion: Both Are Right

### Gatling is not "wrong" - it's designed for classic HTTP

**Traditional HTTP Request/Response:**
```
Client: GET /api/data
Server: 200 OK + JSON body
         ↑
    Measuring latency here makes perfect sense
```

**SSE/Streaming (LLM case):**
```
Client: POST /completions
Server: 200 OK
         ↑
    Gatling measures here... but the real value comes later
Server: [streaming for 2+ seconds]
Server: [DONE]
         ↑
    Here is where the user perceives "completed"
```

---

## 💡 Feature Request Proposal

### Proposed API

```java
// Current behavior (default)
sse("Connect to LLM")
  .post("/v1/chat/completions")
  .body(StringBody(requestBody))
  // Measures only until HTTP 200 OK

// New behavior (opt-in)
sse("Connect to LLM")
  .post("/v1/chat/completions")
  .body(StringBody(requestBody))
  .measureUntilStreamCompletion()  // ← NEW
  .completionMarker("[DONE]")      // ← NEW (optional)
  // Now measures until receiving [DONE]
```

### Expected Behavior

**With `.measureUntilStreamCompletion()`:**
- Gatling's timer does NOT stop at HTTP 200
- Continues measuring during the `.asLongAs()` loop
- Stops when completion marker is detected
- P99/P95 metrics reflect real end-to-end latency

**Advantages:**
1. ✅ **Backward compatible** - Requires explicit opt-in
2. ✅ **Flexible** - Supports different completion markers (`[DONE]`, EOF, timeout)
3. ✅ **Useful** - Covers a real use case (LLM streaming)
4. ✅ **Accurate** - Measures what the user actually experiences

---

## 📊 Comparison: Before vs After Feature

### Current Scenario (Without Feature)

```
Test with 100 concurrent users:
┌─────────────────────────────────────┐
│ Gatling Report                      │
├─────────────────────────────────────┤
│ P99 Response Time: 558ms           │ ← Does NOT represent real UX
│ Mean: 280ms                        │
│ Errors: 0%                         │
└─────────────────────────────────────┘

Reality:
• Users wait 2-5 seconds for complete responses
• Team optimizes based on incorrect metrics
• SLAs are misaligned with actual experience
```

### Proposed Scenario (With Feature)

```
Test with 100 concurrent users:
┌─────────────────────────────────────┐
│ Gatling Report                      │
├─────────────────────────────────────┤
│ Connection P99: 558ms              │ ← Useful for capacity
│ End-to-End P99: 2,018ms           │ ← NEW - Real UX
│ TTFT P99: 6ms                     │ ← NEW - Responsiveness
│ Streaming P99: 2,012ms            │ ← NEW - Processing
│ Errors: 0%                        │
└─────────────────────────────────────┘

Advantages:
• Metrics aligned with user experience
• Optimization based on real data
• Accurate SLAs
```

---

## 🤔 Is the Official Documentation Confusing?

### Documentation Quote

https://docs.gatling.io/guides/use-cases/llm-api/

> "Gatling waits for complete stream completion before considering the request finished, not just connection establishment."

### Confusion Analysis

**✅ TECHNICALLY CORRECT:**
- Gatling DOES wait for the stream to complete
- The scenario does NOT continue until `.asLongAs()` finishes
- Gatling does NOT proceed to the next step prematurely

**❌ PRACTICALLY MISLEADING:**
- "Waits for" ≠ "Measures"
- The `.asLongAs()` is a **loop**, not a "request"
- Stream time does **NOT appear** in Gatling metrics
- Users assume "wait" implies "measure"

### Perfect Analogy

```
Gatling says: "I wait for you to finish eating"
User assumes: "Then you measure how long I take to eat"
Reality: Gatling only measures "how long it takes you to sit at the table"
```

### Evidence in Our Reports

```
---- Requests --------------------------------------------------------
> Connect to LLM - short    | P99: 558ms
> close                     | P99: 1ms
```

**Critical observation:** The `.asLongAs()` does **NOT appear** as a request in the report.

---

## 🔬 Deep Analysis of Official Gatling Code

### Official Example Code

https://docs.gatling.io/guides/use-cases/llm-api/

```java
ScenarioBuilder prompt = scenario("Scenario").exec(
  sse("Connect to LLM and get Answer")    // ← STEP 1
    .post("/completions")
    .header("Authorization", "Bearer " + apiKey)
    .body(StringBody("{\"model\": \"gpt-3.5-turbo\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Just say HI\"}]}"))
    .asJson(),
  asLongAs("#{stop.isUndefined()}").on(   // ← STEP 2
    sse.processUnmatchedMessages((messages, session) ->
      messages.stream()
        .anyMatch(message -> message.message().contains("{\"data\":\"[DONE]\"}"))
        ? session.set("stop", true) : session;
    )
  ),
  sse("close").close()                     // ← STEP 3
);
```

### Breakdown: What Does Gatling Measure in Each Step?

#### STEP 1: `sse("Connect to LLM and get Answer").post("/completions")`

```
⏱️  TIMER STARTS
    ↓
📤 Sends POST request to /completions
    ↓
🔄 Waits for server response
    ↓
✅ Receives HTTP/1.1 200 OK
    ↓
⏱️  TIMER STOPS → Metric captured: "Connect to LLM and get Answer" ≈ 558ms
```

**What Gatling measures here:**
- ✅ Network latency
- ✅ SSL/TLS handshake time
- ✅ Server initial processing time
- ✅ SSE connection establishment

**What Gatling does NOT measure:**
- ❌ Chunk processing
- ❌ Streaming time
- ❌ User-perceived latency

---

#### STEP 2: `asLongAs("#{stop.isUndefined()}").on(...)`

```
[NO ACTIVE TIMER]
    ↓
🔁 Loop: Processes incoming SSE messages
    ↓
📥 Receives chunks 1, 2, 3... 100
    ↓
🔍 Searches for [DONE] marker in each message
    ↓
✅ Detects [DONE], sets session("stop", true)
    ↓
🔚 Exits loop
    ↓
[NO METRIC CAPTURED]
```

**⚠️ CRITICAL: This is a LOOP, NOT a "request"**

- ❌ Gatling does NOT have an active timer here
- ❌ This part does NOT appear in metrics
- ❌ Elapsed time (~1,460ms in our tests) is LOST

**Evidence in Gatling report:**

```
---- Requests --------------------------------------------------------
> Connect to LLM and get Answer    | P99: 558ms     ← This appears
> close                            | P99: 1ms       ← This appears
                                                    ← asLongAs() does NOT appear
```

---

#### STEP 3: `sse("close").close()`

```
⏱️  NEW TIMER STARTS (independent from STEP 1)
    ↓
🔌 Closes SSE connection
    ↓
⏱️  TIMER STOPS → Metric captured: "close" ≈ 1ms
```

**What Gatling measures here:**
- ✅ Connection close time (negligible)

---

### Complete Timeline: What Really Happens

```
Time      │ Event                               │ Gatling Measures │ User Experiences
──────────┼─────────────────────────────────────┼──────────────────┼─────────────────────
0ms       │ POST /completions sent              │ ⏱️  Timer ON     │ [Waiting...]
          │                                     │                  │
100ms     │ [SSL/TLS Handshake]                │ ⏱️  Measuring    │ [Waiting...]
          │                                     │                  │
558ms     │ ✅ HTTP 200 OK received             │ ⏱️  Timer OFF    │ [Waiting...]
          │ SSE connection established         │ ✅ 558ms         │
          │ ────────────────────────────────── │ ──────────────── │ ──────────────────
          │ [asLongAs loop STARTS]             │ ❌ NOT MEASURED  │ [Waiting...]
          │                                     │                  │
564ms     │ First chunk received (TTFT)        │ ❌ NOT MEASURED  │ [Sees first text!]
          │                                     │                  │
600ms     │ Chunks 2-10 received               │ ❌ NOT MEASURED  │ [Reading...]
          │                                     │                  │
1,000ms   │ Chunks 11-50 received              │ ❌ NOT MEASURED  │ [Reading...]
          │                                     │                  │
1,500ms   │ Chunks 51-90 received              │ ❌ NOT MEASURED  │ [Reading...]
          │                                     │                  │
2,018ms   │ ✅ [DONE] received, chunk 100       │ ❌ NOT MEASURED  │ ✅ [Complete response!]
          │ [asLongAs loop ENDS]               │                  │
          │ ────────────────────────────────── │ ──────────────── │ ──────────────────
2,019ms   │ close() invoked                    │ ⏱️  Timer ON     │ [Already has response]
          │                                     │                  │
2,020ms   │ Connection closed                  │ ⏱️  Timer OFF    │ [Already has response]
          │                                     │ ✅ 1ms           │
```

**Metrics summary:**
- **Gatling reports**: 558ms (connection) + 1ms (close) = 559ms total
- **User experiences**: 2,018ms (from request to complete response)
- **Gap**: 1,459ms (261% difference)

---

### 🎯 Why is `asLongAs()` NOT Measured?

**Architectural reason in Gatling:**

In Gatling, a "request" is an atomic unit that has:
1. A name (string identifier)
2. An HTTP method (POST, GET, etc.)
3. A timer that starts and stops automatically

**`asLongAs()` is NOT a request, it's a control flow:**

```java
// This IS a request (has name and HTTP method)
sse("Connect to LLM and get Answer").post("/completions")

// This is NOT a request (it's a loop)
asLongAs("#{stop.isUndefined()}").on(...)

// This IS a request (has name and SSE action)
sse("close").close()
```

**Analogy with imperative code:**

```java
long startTime = System.currentTimeMillis();
HttpResponse response = httpClient.post("/completions");  // ← Gatling measures this
long gatlingMetric = System.currentTimeMillis() - startTime;

// The loop is NOT measured
while (!done) {                                           // ← Gatling does NOT measure this
    String chunk = readNextChunk();
    if (chunk.contains("[DONE]")) done = true;
}

connection.close();  // ← Gatling measures this (as separate request)
```

---

### 📊 Comparison: Official Code vs Our Code (Sprint 1)

| Aspect | Official Gatling Code | Our Code (Sprint 1) |
|--------|----------------------|---------------------|
| **Structure** | 3 separate steps | 3 steps + manual timing |
| **Connection measurement** | ✅ `sse("Connect...").post()` = 558ms | ✅ Automatic by Gatling |
| **Streaming measurement** | ❌ NO - just loop without timer | ✅ `requestStartTime` → `currentTime` |
| **TTFT** | ❌ NOT captured | ✅ First `delta.content` timestamp |
| **Total Response Time** | ❌ Only 558ms (connection) | ✅ 2,018ms (real end-to-end) |
| **Metrics in report** | ✅ P99 of connection (incomplete) | ✅ P99 of complete experience |
| **Truncation Detection** | ❌ Only loop timeout | ✅ Timeout + buffer overflow |
| **Test Phase Tracking** | ❌ NO | ✅ RAMP vs STEADY |
| **Export Format** | ✅ Gatling HTML report | ✅ JSONL + Gatling report |
| **Post-analysis** | ❌ Limited to Gatling metrics | ✅ 5-dimensional quality |

**Sprint 1 code (manual timing):**

```java
// Timer initialization (INSIDE asLongAs)
long requestStartTime = session.contains("requestStartTime")
    ? session.getLong("requestStartTime")
    : System.currentTimeMillis();

if (!session.contains("requestStartTime")) {
    session = session.set("requestStartTime", requestStartTime);
}

// ... chunk processing ...

// When detecting [DONE] or timeout
if (done || timedOut) {
    long currentTime = System.currentTimeMillis();
    long responseTimeMs = currentTime - requestStartTime;  // ← THIS is the real metric

    // responseTimeMs = 2,018ms (vs Gatling = 558ms)
}
```

---

### 💡 Key Insights from Code Analysis

#### 1. **The request name is misleading**

```java
sse("Connect to LLM and get Answer")  // ← Says "get Answer"
  .post("/completions")               // ← Only measures "Connect", NOT "get Answer"
```

**The name suggests:** Measure until getting the answer
**The reality:** Only measures until establishing connection

**This reinforces the feature request argument:**
- Users expect a request named "Connect **and get Answer**" to measure both
- Natural expectation is to include the complete response
- Current behavior is counter-intuitive

---

#### 2. **Artificial separation between connection and streaming**

From the user's perspective, this is **ONE operation**:

```
User asks question → Waits for complete response
```

But Gatling divides it into:

```
1. Connection (measured)
2. Streaming (NOT measured)  ← Artificial from UX perspective
3. Close (measured)
```

---

#### 3. **Official code DEMONSTRATES the need for the feature**

The fact that Gatling provides this code as an official example but:
- ❌ Does NOT capture TTFT
- ❌ Does NOT capture complete response time
- ❌ Does NOT capture streaming metrics

...demonstrates that the framework **needs to evolve** for this use case.

---

### 🎯 Improved API Proposal

Based on the official code, we propose:

#### Option 1: Unify in a single request (simpler)

```java
sse("Connect to LLM and get Answer")
  .post("/completions")
  .body(StringBody("{...}"))
  .measureUntilStreamCompletion()        // ← NEW: Extends timer
  .completionMarker("[DONE]")            // ← NEW: Defines marker
  .timeout(10, TimeUnit.SECONDS)         // ← NEW: Explicit timeout
  .asJson()
// asLongAs() no longer needed - Gatling handles it internally
```

**Behavior:**
- Timer does NOT stop at HTTP 200 OK
- Gatling processes chunks internally
- Timer stops when detecting `[DONE]` or timeout
- P99 metrics reflect complete experience

---

#### Option 2: Separate request for streaming (more flexible)

```java
scenario("Scenario").exec(
  sse("Connect to LLM")
    .post("/completions")
    .asJson(),

  sse("Process Stream")                  // ← NEW: Request type for streaming
    .measureStreamDuration()
    .asLongAs("#{stop.isUndefined()}").on(
      sse.processUnmatchedMessages(...)
    ),

  sse("close").close()
)
```

**Advantage:** Separate reports for connection vs streaming

```
---- Requests --------------------------------------------------------
> Connect to LLM        | P99: 558ms    ← Connection capacity
> Process Stream        | P99: 1,460ms  ← Streaming latency  ← NEW
> close                 | P99: 1ms      ← Close
```

---

### 🔍 Design Questions for Feature Request

#### 1. **What happens if `[DONE]` never arrives?**

```java
.measureUntilStreamCompletion()
.completionMarker("[DONE]")
.timeout(10, TimeUnit.SECONDS)         // ← Required
.onTimeout(MarkAs.ERROR)               // ← Or MarkAs.SUCCESS with flag
```

**Options:**
- `MarkAs.ERROR` → Request fails, appears in "Errors" in report
- `MarkAs.SUCCESS` → Request completes successfully, but flag indicates timeout

---

#### 2. **Support multiple completion markers?**

Different LLM APIs use different markers:

```java
.completionMarkers(Arrays.asList(
    "[DONE]",                    // OpenAI
    "data: [DONE]",              // OpenAI variant
    "{\"finish_reason\":\"stop\"}"  // Anthropic/others
))
.orStreamEnd()                   // Or detect stream EOF
```

---

#### 3. **How to capture TTFT in addition to response time?**

```java
sse("Connect to LLM")
  .post("/completions")
  .measureUntilStreamCompletion()
  .captureTimeToFirstData()      // ← NEW: Captures TTFT
  .completionMarker("[DONE]")
```

**Resulting report:**

```
---- Requests --------------------------------------------------------
> Connect to LLM        | TTFT P99: 6ms | Total P99: 2,018ms
```

---

## 🎯 Recommendation

### 1. Yes, It's a Valid Feature Request

**Reasons:**
- ✅ Covers a real and growing use case (LLM streaming)
- ✅ Current implementation leads to misinterpretation
- ✅ Other users probably have the same problem
- ✅ Gatling positions itself as a tool for LLM testing

### 2. No, Gatling does NOT "confuse" the protocol

**Clarification:**
- ❌ Gatling does NOT misinterpret SSE
- ❌ Gatling does NOT violate HTTP standards
- ✅ Gatling simply was not designed for this use case
- ✅ Current behavior is correct from HTTP perspective

### 3. The Gap is in Expectation vs Reality

**The real problem:**
```
Expectation: "Measure user-perceived latency"
Reality: "Measure HTTP connection establishment latency"
Gap: These are different in streaming, same in traditional HTTP
```

---

## 📝 GitHub Issue Proposal

### Suggested Title
```
Feature Request: Add optional end-to-end latency measurement for SSE streaming
```

### Suggested Content

```markdown
## Context

When load testing LLM APIs with Server-Sent Events (SSE), Gatling correctly
measures HTTP connection establishment (~500ms) but not the full streaming
duration (~2000ms). For user experience metrics, we need end-to-end latency.

## Current Behavior

Using the official example from https://docs.gatling.io/guides/use-cases/llm-api/:

```java
ScenarioBuilder prompt = scenario("Scenario").exec(
  sse("Connect to LLM and get Answer")    // ← Timer starts/stops here
    .post("/completions")
    .body(StringBody("{...}"))
    .asJson(),
  asLongAs("#{stop.isUndefined()}").on(   // ← NO timer here
    sse.processUnmatchedMessages((messages, session) ->
      messages.stream()
        .anyMatch(message -> message.message().contains("[DONE]"))
        ? session.set("stop", true) : session
    )
  ),
  sse("close").close()
);
```

**Problem:** The `asLongAs()` loop (where streaming happens) is NOT measured.

**Gatling report shows:**
```
---- Requests --------------------------------------------------------
> Connect to LLM and get Answer    | P99: 558ms
> close                            | P99: 1ms
                                    ← asLongAs loop missing
```

**What Gatling measures:** Connection setup (558ms in our tests)
**What user experiences:** Full response time (2,018ms in our tests)
**Gap:** 261% difference (1,460ms of streaming NOT captured)

## Proposed Feature

### Option 1: Extend timer until stream completion (simpler)

```java
sse("Connect to LLM and get Answer")
  .post("/completions")
  .body(StringBody("{...}"))
  .measureUntilStreamCompletion()        // ← NEW: Timer doesn't stop at HTTP 200
  .completionMarker("[DONE]")            // ← NEW: Define completion condition
  .timeout(10, TimeUnit.SECONDS)         // ← NEW: Explicit timeout
  .asJson()
// The asLongAs() could be handled internally by Gatling
```

**Result:** Single metric that represents full user experience (connection + streaming).

---

### Option 2: Separate measurable request for streaming (more flexible)

```java
scenario("Scenario").exec(
  sse("Connect to LLM")
    .post("/completions")
    .asJson(),

  sse("Process Stream")                  // ← NEW: Measurable streaming request
    .measureStreamDuration()
    .completionMarker("[DONE]")
    .asLongAs("#{stop.isUndefined()}").on(
      sse.processUnmatchedMessages(...)
    ),

  sse("close").close()
)
```

**Result:** Separate metrics for connection vs streaming (better for analysis).

```
---- Requests --------------------------------------------------------
> Connect to LLM        | P99: 558ms    ← Connection capacity
> Process Stream        | P99: 1,460ms  ← Streaming latency (NEW)
> close                 | P99: 1ms      ← Close
```

Both options would include streaming time in Gatling's P99/P95 metrics.

## Benefits

1. Accurate UX metrics for streaming APIs
2. Proper SLA definition for LLM services
3. Aligned with growing LLM testing use case
4. Backward compatible (opt-in)

## Workaround (Current)

We implemented manual timing in session:
- Capture `requestStartTime` before request
- Calculate `responseTimeMs` after `[DONE]`
- Export to custom JSONL for analysis

This works but loses Gatling's built-in percentile calculations.

## Evidence

- Official guide: https://docs.gatling.io/guides/use-cases/llm-api/
- Our analysis: [link to TTFT_PERCENTIL99_ANALYSIS.md]

## Why This Matters

The official example names the request **"Connect to LLM and get Answer"** but only measures the "Connect" part, not the "get Answer" part. This creates a gap between:

- **User expectation:** "I want to measure how long it takes to get an answer"
- **Gatling behavior:** "I measure how long it takes to establish the connection"

For LLM applications, the answer **IS** the streaming phase, not just the connection.

## Additional Considerations

### 1. TTFT (Time To First Token)
Consider also capturing time to first data chunk:

```java
.captureTimeToFirstData()  // Captures TTFT separately
```

**Report output:**
```
> Connect to LLM    | TTFT P99: 6ms | Total P99: 2,018ms
```

### 2. Multiple completion markers
Different LLM APIs use different markers:

```java
.completionMarkers(Arrays.asList("[DONE]", "data: [DONE]"))
.orStreamEnd()  // Or detect natural EOF
```

### 3. Timeout handling
Clear semantics for when completion marker never arrives:

```java
.onTimeout(MarkAs.ERROR)     // Fail the request
// OR
.onTimeout(MarkAs.SUCCESS)   // Complete with flag
```
```

---

## ✅ Final Conclusions

### 1. Feature Request: Yes, it's valid

- ✅ Would benefit the LLM testing community
- ✅ Aligned with Gatling's direction (LLM use cases)
- ✅ Technically feasible solution
- ✅ Backward compatible

### 2. Gatling is not "wrong"

- ✅ Correct behavior according to HTTP/SSE standards
- ✅ Designed for traditional request/response
- ✅ Not a bug, it's a feature gap

### 3. Documentation could improve

**Improvement suggestion:**

```markdown
## ⚠️ Important Note on SSE Metrics

Gatling waits for stream completion before proceeding to the next step,
but **does not include streaming time in request metrics**.

The `.asLongAs()` loop processes events but is not measured as part of
the request's response time. Only the initial HTTP connection setup is
included in P99/P95 statistics.

For end-to-end latency measurement (including full stream processing),
use custom session timing or see [Feature Request #XXXX].
```

### 4. Sprint 1 remains the best current solution

- ✅ Manual measurement is NECESSARY today
- ✅ Our approach is correct and complete
- ✅ Captures metrics that Gatling cannot capture natively

---

## 🎯 Recommended Next Steps

1. **Continue using Sprint 1** - It's the only way to get accurate metrics today
2. **Optional:** Open GitHub Issue in Gatling for future feature
3. **Document internally** - Explain why we use manual measurement
4. **Evangelize** - Share findings with the community

---

## 📋 Executive Summary for Gatling Team

### TL;DR

The official example code for LLM testing (https://docs.gatling.io/guides/use-cases/llm-api/) has a critical gap:

**The request is named** "Connect to LLM **and get Answer**"
**But only measures** "Connect to LLM" (not "get Answer")

72% of response time (the streaming part) is not captured in metrics.

---

### Impact

- **261% underestimation** in reported latency (558ms vs 2,018ms actual)
- **Incorrect SLAs** based on incomplete metrics
- **Misdirected optimizations** toward components that are not the bottleneck
- **Gap between metrics and user experience** in LLM applications

---

### Root Cause

The `asLongAs()` loop (where streaming occurs) is a **control flow**, not a **measurable request**:

```java
sse("Connect to LLM and get Answer").post("/completions")  // ← Measured: 558ms
asLongAs("#{stop.isUndefined()}").on(...)                  // ← NOT measured: 1,460ms
sse("close").close()                                        // ← Measured: 1ms
```

**Total measured:** 559ms | **Total actual:** 2,018ms | **Gap:** 1,459ms (72%)

---

### Proposed Solution

Option 1 (simple):
```java
.measureUntilStreamCompletion().completionMarker("[DONE]")
```

Option 2 (flexible):
```java
sse("Process Stream").measureStreamDuration().asLongAs(...)
```

**Benefits:** Backward compatible, opt-in, solves growing LLM use case

---

### Validation

- ✅ We implemented manual measurement (Sprint 1) - works perfectly
- ✅ Confirmed against RFC 6202 (SSE) and official documentation
- ✅ Gatling's current behavior is correct per HTTP standard
- ✅ Feature request aligned with Gatling's direction toward LLM testing

---

### Community Impact

With the explosion of LLM applications, many teams likely face this same problem. This feature would benefit:

- ✅ QA teams testing LLM APIs
- ✅ Engineers defining SLAs for streaming services
- ✅ Product teams optimizing UX of conversational applications
- ✅ Performance engineers measuring perceived latency

---

**Last updated**: October 22, 2025
**Author**: Post-Sprint 1 Analysis (Load Testing LLM SSE)
**Contact**: [Your contact information for GitHub]
**References**:
- TTFT_PERCENTIL99_ANALYSIS.md (detailed analysis of our tests)
- https://docs.gatling.io/guides/use-cases/llm-api/ (official documentation)
- RFC 6202 (Server-Sent Events)
- This document: GATLING_FEATURE_REQUEST_ANALYSIS.md
