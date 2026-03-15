#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_WORKSPACE:?GITHUB_WORKSPACE is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME is required}"
: "${GOMOBILE_VERSION:?GOMOBILE_VERSION is required}"
: "${LIBBOX_BUILD_TAGS:?LIBBOX_BUILD_TAGS is required}"
: "${SING_BOX_VERSION:?SING_BOX_VERSION is required}"

# Install gomobile / gobind (same version as SFA official)
go install github.com/sagernet/gomobile/cmd/gomobile@"${GOMOBILE_VERSION}"
go install github.com/sagernet/gomobile/cmd/gobind@"${GOMOBILE_VERSION}"
export PATH="$(go env GOPATH)/bin:${PATH}"

# Clone sing-box source at the resolved stable tag
git clone --depth 1 --branch "${SING_BOX_VERSION}" \
  https://github.com/SagerNet/sing-box.git "${RUNNER_TEMP}/sing-box"
cd "${RUNNER_TEMP}/sing-box"

SING_VERSION="${SING_BOX_VERSION#v}"
echo "Building libbox ${SING_VERSION} with tags: ${LIBBOX_BUILD_TAGS}"
if [[ ",${LIBBOX_BUILD_TAGS}," == *",with_clash_api,"* ]]; then
  echo "::error::with_clash_api should not be present in LIBBOX_BUILD_TAGS after the runtime-log patch"
  exit 1
fi

# Current sing-box upstream treats `PlatformLogWriter != nil` as a reason to enable
# extra services automatically. AeroBox only uses PlatformLogWriter for runtime log
# callbacks, and does not depend on the Clash API gRPC surface or cache-file side
# effects. Keep these patches explicit and fail loudly if upstream refactors the
# target blocks.
python3 - <<'PY'
from pathlib import Path

path = Path("box.go")
text = path.read_text()
cache_old = (
    "\tif experimentalOptions.CacheFile != nil && experimentalOptions.CacheFile.Enabled || options.PlatformLogWriter != nil {\n"
    "\t\tneedCacheFile = true\n"
    "\t}\n"
)
cache_new = (
    "\tif experimentalOptions.CacheFile != nil && experimentalOptions.CacheFile.Enabled {\n"
    "\t\tneedCacheFile = true\n"
    "\t}\n"
)
clash_old = (
    "\tif experimentalOptions.ClashAPI != nil || options.PlatformLogWriter != nil {\n"
    "\t\tneedClashAPI = true\n"
    "\t}\n"
)
clash_new = (
    "\tif experimentalOptions.ClashAPI != nil {\n"
    "\t\tneedClashAPI = true\n"
    "\t}\n"
)

cache_count = text.count(cache_old)
if cache_count != 1:
    raise SystemExit(
        f"Failed to patch needCacheFile condition in box.go: expected exactly 1 match, found {cache_count}. "
        "Review upstream sing-box box.go before releasing."
    )
clash_count = text.count(clash_old)
if clash_count != 1:
    raise SystemExit(
        f"Failed to patch needClashAPI condition in box.go: expected exactly 1 match, found {clash_count}. "
        "Review upstream sing-box box.go before releasing."
    )
patched = text.replace(cache_old, cache_new, 1).replace(clash_old, clash_new, 1)
if cache_old in patched or cache_new not in patched:
    raise SystemExit("Patch verification failed for box.go needCacheFile condition")
if clash_old in patched or clash_new not in patched:
    raise SystemExit("Patch verification failed for box.go needClashAPI condition")
path.write_text(patched)
PY

echo "--- patched box.go PlatformLogWriter conditions ---"
grep -n "needCacheFile\\|needClashAPI" box.go
python3 - <<'PY'
from pathlib import Path

text = Path("box.go").read_text()
forbidden = [
    "\tif experimentalOptions.CacheFile != nil && experimentalOptions.CacheFile.Enabled || options.PlatformLogWriter != nil {\n",
    "\tif experimentalOptions.ClashAPI != nil || options.PlatformLogWriter != nil {\n",
]
for old in forbidden:
    if old in text:
        raise SystemExit(
            "PlatformLogWriter still affects needCacheFile/needClashAPI in patched box.go"
        )
print("--- remaining PlatformLogWriter references ---")
for line_no, line in enumerate(text.splitlines(), 1):
    if "PlatformLogWriter" in line:
        print(f"{line_no}:{line}")
PY

cat > experimental/libbox/urltest_export.go <<'EOF'
package libbox

import (
    "context"
    "fmt"
    "strings"
    "time"

    box "github.com/sagernet/sing-box"
    "github.com/sagernet/sing-box/common/urltest"
    "github.com/sagernet/sing-box/experimental/v2rayapi"
    "github.com/sagernet/sing-box/option"
    E "github.com/sagernet/sing/common/exceptions"
    "google.golang.org/grpc"
    "google.golang.org/grpc/credentials/insecure"
)

func URLTestOutbound(configContent string, outboundTag string, testURL string, timeout int32) (int32, error) {
    ctx := baseContext(nil)
    options, err := parseConfig(ctx, configContent)
    if err != nil {
        return 0, E.Cause(err, "parse config")
    }

    ctx, cancel := context.WithCancel(ctx)
    defer cancel()

    if outboundTag == "" {
        outboundTag = "proxy"
    }
    if options.Route == nil {
        options.Route = &option.RouteOptions{}
    }
    options.Route.Final = outboundTag

    instance, err := box.New(box.Options{
        Context: ctx,
        Options: options,
    })
    if err != nil {
        return 0, E.Cause(err, "create box")
    }
    defer instance.Close()

    if err = instance.Start(); err != nil {
        return 0, E.Cause(err, "start box")
    }

    outbound, loaded := instance.Outbound().Outbound(outboundTag)
    if !loaded || outbound == nil {
        outbound = instance.Outbound().Default()
    }
    if outbound == nil {
        return 0, E.New("outbound not available: ", outboundTag)
    }

    testCtx := ctx
    if timeout > 0 {
        var timeoutCancel context.CancelFunc
        testCtx, timeoutCancel = context.WithTimeout(ctx, time.Duration(timeout)*time.Millisecond)
        defer timeoutCancel()
    }

    if testURL == "" {
        testURL = "http://cp.cloudflare.com/"
    }
    delay, err := urltest.URLTest(testCtx, testURL, outbound)
    if err != nil {
        return 0, E.Cause(err, "perform url test")
    }
    return int32(delay), nil
}

func QueryV2RayOutboundStats(apiAddress string, outboundTags string) (string, error) {
    if apiAddress == "" {
        return "", E.New("empty v2ray api address")
    }

    conn, err := grpc.NewClient(
        apiAddress,
        grpc.WithTransportCredentials(insecure.NewCredentials()),
    )
    if err != nil {
        return "", E.Cause(err, "dial v2ray api")
    }
    defer conn.Close()

    patterns := make([]string, 0)
    for _, tag := range strings.Split(outboundTags, ",") {
        trimmed := strings.TrimSpace(tag)
        if trimmed == "" {
            continue
        }
        patterns = append(patterns, "outbound>>>"+trimmed+">>>traffic")
    }
    if len(patterns) == 0 {
        patterns = append(patterns, "outbound>>>proxy>>>traffic")
    }

    ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
    defer cancel()

    request := &v2rayapi.QueryStatsRequest{
        Patterns: patterns,
    }
    response := new(v2rayapi.QueryStatsResponse)
    err = conn.Invoke(
        ctx,
        "/v2ray.core.app.stats.command.StatsService/QueryStats",
        request,
        response,
    )
    if err != nil {
        return "", E.Cause(err, "query stats")
    }

    var upload int64
    var download int64
    for _, stat := range response.GetStat() {
        switch {
        case strings.HasSuffix(stat.GetName(), ">>>uplink"):
            upload += stat.GetValue()
        case strings.HasSuffix(stat.GetName(), ">>>downlink"):
            download += stat.GetValue()
        }
    }

    return fmt.Sprintf("%d,%d", upload, download), nil
}
EOF
gofmt -w experimental/libbox/urltest_export.go
echo "--- added experimental/libbox/urltest_export.go ---"
sed -n '1,200p' experimental/libbox/urltest_export.go

# Build with gomobile bind directly (no need to patch build_libbox)
mkdir -p "${GITHUB_WORKSPACE}/app/build/libbox"
gomobile bind -v \
  -o "${GITHUB_WORKSPACE}/app/build/libbox/libbox.aar" \
  -target android \
  -androidapi 31 \
  -javapkg=io.nekohasekai \
  -libname=box \
  -trimpath \
  -buildvcs=false \
  -ldflags "-X github.com/sagernet/sing-box/constant.Version=${SING_VERSION} -s -w -buildid= -checklinkname=0" \
  -tags "${LIBBOX_BUILD_TAGS}" \
  ./experimental/libbox

AAR_PATH="${GITHUB_WORKSPACE}/app/build/libbox/libbox.aar"
echo "--- libbox.aar built ---"
ls -lh "${AAR_PATH}"

STRIP_DIR="${RUNNER_TEMP}/aar_strip"
mkdir -p "${STRIP_DIR}"
unzip -q "${AAR_PATH}" -d "${STRIP_DIR}"
LLVM_STRIP="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
if [ -x "${LLVM_STRIP}" ]; then
  find "${STRIP_DIR}/jni" -name '*.so' -exec "${LLVM_STRIP}" --strip-unneeded {} \;
  echo "--- .so sizes after strip ---"
  find "${STRIP_DIR}/jni" -name '*.so' -exec du -h {} \; | sort -h
  pushd "${STRIP_DIR}"
  zip -q -r "${AAR_PATH}" .
  popd
fi
