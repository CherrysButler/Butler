// butler-turnstile: the Turnstile check for Butler on Windows, in WebView2 (Edge's engine,
// part of Windows). Butler starts it with the page to show; it serves that page as
// https://janitorai.com/ (the origin Janitor's site key is bound to) and writes one line per
// event to stdout:
//
//   agent <the WebView's User-Agent>
//   token <the Turnstile token>
//   error <code>
//   cookie <name>=<value>      (janitorai.com's, after the token: Cloudflare's clearance)
//   done
//
// Usage: butler-turnstile.exe <page.html> <title> <dark|light> [<owner hwnd> <x> <y> <w> <h> <RRGGBB>]
//
// With an owner, the check is a borderless panel owned by Butler's window at x, y (screen
// pixels), w by h, on the RRGGBB ground: it sits inside Butler's own dialog, with no title bar
// and no taskbar button. Without one, it is a small window of its own.

#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <shlwapi.h>
#include <shellapi.h>
#include <wrl.h>
#include <WebView2.h>
#include <string>
#include <fstream>
#include <sstream>

using Microsoft::WRL::Callback;
using Microsoft::WRL::ComPtr;

namespace {

const wchar_t *kOrigin = L"https://janitorai.com/";
HWND window = nullptr;
COLORREF ground = RGB(0x2B, 0x2E, 0x33);
ComPtr<ICoreWebView2Environment> environment;
ComPtr<ICoreWebView2Controller> controller;
ComPtr<ICoreWebView2> view;
std::string page;
bool finished = false;

std::string utf8(const std::wstring &w)
{
    if (w.empty()) return {};
    int n = WideCharToMultiByte(CP_UTF8, 0, w.data(), int(w.size()), nullptr, 0, nullptr, nullptr);
    std::string s(n, '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.data(), int(w.size()), s.data(), n, nullptr, nullptr);
    return s;
}

std::wstring wide(const std::string &s)
{
    if (s.empty()) return {};
    int n = MultiByteToWideChar(CP_UTF8, 0, s.data(), int(s.size()), nullptr, 0);
    std::wstring w(n, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, s.data(), int(s.size()), w.data(), n);
    return w;
}

std::wstring take(LPWSTR s)
{
    std::wstring out = s ? s : L"";
    CoTaskMemFree(s);
    return out;
}

void say(const std::string &line)
{
    std::string out = line + "\n";
    DWORD written = 0;
    WriteFile(GetStdHandle(STD_OUTPUT_HANDLE), out.data(), DWORD(out.size()), &written, nullptr);
    FlushFileBuffers(GetStdHandle(STD_OUTPUT_HANDLE));
}

/** After the token: hand over janitorai.com's cookies (the clearance), then close. */
void finish()
{
    if (finished) return;
    finished = true;
    ComPtr<ICoreWebView2_2> view2;
    ComPtr<ICoreWebView2CookieManager> cookies;
    if (SUCCEEDED(view.As(&view2)) && SUCCEEDED(view2->get_CookieManager(&cookies))) {
        cookies->GetCookies(kOrigin, Callback<ICoreWebView2GetCookiesCompletedHandler>(
            [](HRESULT, ICoreWebView2CookieList *list) -> HRESULT {
                UINT count = 0;
                if (list) list->get_Count(&count);
                for (UINT i = 0; i < count; ++i) {
                    ComPtr<ICoreWebView2Cookie> c;
                    if (FAILED(list->GetValueAtIndex(i, &c))) continue;
                    LPWSTR name = nullptr, value = nullptr;
                    c->get_Name(&name);
                    c->get_Value(&value);
                    say("cookie " + utf8(take(name)) + "=" + utf8(take(value)));
                }
                say("done");
                PostMessage(window, WM_CLOSE, 0, 0);
                return S_OK;
            }).Get());
    } else {
        say("done");
        PostMessage(window, WM_CLOSE, 0, 0);
    }
}

void resize()
{
    if (!controller) return;
    RECT r;
    GetClientRect(window, &r);
    controller->put_Bounds(r);
}

LRESULT CALLBACK proc(HWND h, UINT msg, WPARAM wp, LPARAM lp)
{
    switch (msg) {
    case WM_SIZE:
        resize();
        return 0;
    case WM_CLOSE:
        if (controller) controller->Close();
        DestroyWindow(h);
        return 0;
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(h, msg, wp, lp);
}

HRESULT onController(HRESULT result, ICoreWebView2Controller *created)
{
    if (FAILED(result) || !created) {
        say("error webview");
        PostMessage(window, WM_CLOSE, 0, 0);
        return S_OK;
    }
    controller = created;
    controller->get_CoreWebView2(&view);

    // The page is transparent; under it, the dialog's own ground.
    ComPtr<ICoreWebView2Controller2> controller2;
    if (SUCCEEDED(controller.As(&controller2)))
        controller2->put_DefaultBackgroundColor({ 255, GetRValue(ground), GetGValue(ground), GetBValue(ground) });

    ComPtr<ICoreWebView2Settings> settings;
    view->get_Settings(&settings);
    settings->put_IsScriptEnabled(TRUE);
    settings->put_IsStatusBarEnabled(FALSE);
    settings->put_AreDefaultContextMenusEnabled(FALSE);
    settings->put_AreDevToolsEnabled(FALSE);
    ComPtr<ICoreWebView2Settings2> settings2;
    if (SUCCEEDED(settings.As(&settings2))) {
        LPWSTR ua = nullptr;
        if (SUCCEEDED(settings2->get_UserAgent(&ua))) say("agent " + utf8(take(ua)));
    }

    // The page's `Butler` object, as Android's JavaScript interface: its calls come back as messages.
    view->AddScriptToExecuteOnDocumentCreated(
        L"window.Butler={onToken:function(t){chrome.webview.postMessage('token '+t)},"
        L"onError:function(c){chrome.webview.postMessage('error '+c)}};",
        nullptr);

    // Butler's page, answered locally for the origin's own address only.
    view->AddWebResourceRequestedFilter(kOrigin, COREWEBVIEW2_WEB_RESOURCE_CONTEXT_DOCUMENT);
    EventRegistrationToken t1, t2;
    view->add_WebResourceRequested(Callback<ICoreWebView2WebResourceRequestedEventHandler>(
        [](ICoreWebView2 *, ICoreWebView2WebResourceRequestedEventArgs *args) -> HRESULT {
            ComPtr<ICoreWebView2WebResourceRequest> request;
            args->get_Request(&request);
            LPWSTR uri = nullptr;
            request->get_Uri(&uri);
            if (take(uri) != kOrigin) return S_OK;
            ComPtr<IStream> stream;
            stream.Attach(SHCreateMemStream(reinterpret_cast<const BYTE *>(page.data()), UINT(page.size())));
            ComPtr<ICoreWebView2WebResourceResponse> response;
            environment->CreateWebResourceResponse(stream.Get(), 200, L"OK", L"Content-Type: text/html; charset=utf-8", &response);
            args->put_Response(response.Get());
            return S_OK;
        }).Get(), &t1);

    view->add_WebMessageReceived(Callback<ICoreWebView2WebMessageReceivedEventHandler>(
        [](ICoreWebView2 *, ICoreWebView2WebMessageReceivedEventArgs *args) -> HRESULT {
            LPWSTR text = nullptr;
            if (FAILED(args->TryGetWebMessageAsString(&text))) return S_OK;
            std::string line = utf8(take(text));
            say(line);
            if (line.rfind("token ", 0) == 0) {
                // The clearance can land a moment after the token.
                SetTimer(window, 1, 1500, [](HWND, UINT, UINT_PTR, DWORD) { finish(); });
            }
            return S_OK;
        }).Get(), &t2);

    resize();
    view->Navigate(kOrigin);
    return S_OK;
}

} // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int)
{
    int argc = 0;
    LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (argc < 3) {
        say("error usage");
        return 2;
    }
    {
        std::ifstream in(argv[1], std::ios::binary);
        std::stringstream buffer;
        buffer << in.rdbuf();
        page = buffer.str();
    }
    const std::wstring title = argv[2];
    const bool dark = argc > 3 && std::wstring(argv[3]) == L"dark";
    ground = dark ? RGB(0x2B, 0x2E, 0x33) : RGB(0xF3, 0xF3, 0xF1);
    const bool embedded = argc > 9;
    HWND owner = nullptr;
    int ex = 0, ey = 0, ew = 0, eh = 0;
    if (embedded) {
        owner = reinterpret_cast<HWND>(static_cast<LONG_PTR>(_wtoi64(argv[4])));
        ex = _wtoi(argv[5]);
        ey = _wtoi(argv[6]);
        ew = _wtoi(argv[7]);
        eh = _wtoi(argv[8]);
        const unsigned long rgb = wcstoul(argv[9], nullptr, 16);
        ground = RGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        if (!IsWindow(owner)) owner = nullptr;
    }

    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);

    WNDCLASSW wc {};
    wc.lpfnWndProc = proc;
    wc.hInstance = instance;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = CreateSolidBrush(ground);
    wc.hIcon = LoadIcon(instance, MAKEINTRESOURCE(1));
    wc.lpszClassName = L"ButlerTurnstile";
    RegisterClassW(&wc);

    if (embedded && owner) {
        // Owned by Butler: it stays above Butler, minimises with it, and has no button of its own.
        window = CreateWindowExW(WS_EX_TOOLWINDOW, wc.lpszClassName, title.c_str(), WS_POPUP,
            ex, ey, ew, eh, owner, nullptr, instance, nullptr);
    } else {
        const UINT dpi = GetDpiForSystem();
        const int w = MulDiv(380, dpi, 96), h = MulDiv(200, dpi, 96);
        RECT work;
        SystemParametersInfoW(SPI_GETWORKAREA, 0, &work, 0);
        window = CreateWindowExW(WS_EX_TOPMOST, wc.lpszClassName, title.c_str(),
            WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU,
            (work.left + work.right - w) / 2, (work.top + work.bottom - h) / 2, w, h,
            nullptr, nullptr, instance, nullptr);
    }
    ShowWindow(window, SW_SHOWNOACTIVATE);

    wchar_t data[MAX_PATH];
    DWORD n = GetEnvironmentVariableW(L"LOCALAPPDATA", data, MAX_PATH);
    std::wstring folder = (n ? std::wstring(data, n) : std::wstring(L".")) + L"\\Butler\\WebView2";

    HRESULT started = CreateCoreWebView2EnvironmentWithOptions(nullptr, folder.c_str(), nullptr,
        Callback<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>(
            [](HRESULT result, ICoreWebView2Environment *env) -> HRESULT {
                if (FAILED(result) || !env) {
                    say("error webview-runtime");
                    PostMessage(window, WM_CLOSE, 0, 0);
                    return S_OK;
                }
                environment = env;
                return env->CreateCoreWebView2Controller(window,
                    Callback<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler>(onController).Get());
            }).Get());
    if (FAILED(started)) {
        say("error webview-runtime");
        return 1;
    }

    MSG msg;
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    if (!finished) say("error closed");
    CoUninitialize();
    return 0;
}
