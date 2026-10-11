use std::{
    ffi::{c_char, c_int, CString},
    io,
};

use jni::errors::ThrowRuntimeExAndDefault;
use jni::jni_mangle;
use jni::objects::{JClass, JObject};
use jni::EnvUnowned;

const LOG_TAG: &std::ffi::CStr = c"PockyCore";
const LOG_INFO: c_int = 4;
const LOG_FILTER: &str =
    "error,matrix_sdk_crypto::verification=info,matrix_sdk::encryption::verification=info";

#[link(name = "log")]
extern "C" {
    fn __android_log_write(priority: c_int, tag: *const c_char, text: *const c_char) -> c_int;
}

struct Logcat;

impl io::Write for Logcat {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        let text = String::from_utf8_lossy(buf);
        let line = text.trim_end();
        if !line.is_empty() {
            if let Ok(line) = CString::new(line.replace('\0', "")) {
                unsafe {
                    __android_log_write(LOG_INFO, LOG_TAG.as_ptr(), line.as_ptr());
                }
            }
        }
        Ok(buf.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

fn start_logging() {
    let _ = tracing_subscriber::fmt()
        .with_writer(|| Logcat)
        .with_ansi(false)
        .without_time()
        .with_env_filter(tracing_subscriber::EnvFilter::new(LOG_FILTER))
        .try_init();
}

#[jni_mangle("com.pokecio.app.tls.TlsVerifier")]
pub fn init<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    context: JObject<'caller>,
) {
    start_logging();
    unowned_env
        .with_env(|env| rustls_platform_verifier::android::init_with_env(env, context))
        .resolve::<ThrowRuntimeExAndDefault>();
}
