#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>
#include <stdlib.h>
#include <termios.h>
#include <string.h>
#include <errno.h>

#ifndef MODULE_NAME
#define MODULE_NAME  "TERMINAL"
#endif

#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, MODULE_NAME, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, MODULE_NAME, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, MODULE_NAME, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, MODULE_NAME, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, MODULE_NAME, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL, MODULE_NAME, __VA_ARGS__)

#define ALPINE_TERM_UNUSED(x) x __attribute__((__unused__))

static int throw_runtime_exception(JNIEnv* env, char const* message)
{
    jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    (*env)->ThrowNew(env, exClass, message);
    return -1;
}

JNIEXPORT jint JNICALL
Java_alpine_term_JNI_getPid(JNIEnv *ALPINE_TERM_UNUSED(env), jclass ALPINE_TERM_UNUSED(clazz)) {
    return getpid();
}


JNIEXPORT jint JNICALL
Java_alpine_term_JNI_createPseudoTerminal(
    JNIEnv *env,
    jclass ALPINE_TERM_UNUSED(clazz)
) {
    LOGV("opening ptmx (master) device");
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    LOGV("opened ptmx (master) device: %d", ptm);
    if (ptm < 0) return throw_runtime_exception(env, "Cannot open /dev/ptmx");

#ifdef LACKS_PTSNAME_R
    char* devname;
#else
    char devname[64];
#endif
    if (grantpt(ptm) || unlockpt(ptm) ||
        #ifdef LACKS_PTSNAME_R
        (devname = ptsname(ptm)) == NULL
        #else
        ptsname_r(ptm, devname, sizeof(devname))
#endif
        ) {
        return throw_runtime_exception(env, "Cannot grantpt()/unlockpt()/ptsname_r() on /dev/ptmx");
    }

    // Enable UTF-8 mode.
    struct termios tios;
    tcgetattr(ptm, &tios);
    tios.c_iflag |= IUTF8;
    tcsetattr(ptm, TCSANOW, &tios);

    /** Set initial winsize to 80x80. */
    struct winsize sz = { .ws_row = 80, .ws_col = 80 };
    ioctl(ptm, TIOCSWINSZ, &sz);

    // Clear signals which the Android java process may have blocked:
    sigset_t signals_to_unblock;
    sigfillset(&signals_to_unblock);
    sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);

    setsid();

    LOGV("opening %s (slave) device", devname);
    int pts = open(devname, O_RDWR);
    if (pts < 0) {
        LOGE("cannot open %s (slave) device: %d (error: %s)", devname, pts, strerror(errno));
        return throw_runtime_exception(env, "failed to open slave device");
    }
    LOGV("opened %s (slave) device: %d", devname, pts);
    LOGV("duping current stdin (fd 0), stdout (fd 1), and stderr (fd 2) to %s (slave) device: %d", devname, pts);
    dup2(pts, 0);
    dup2(pts, 1);
    dup2(pts, 2);
    printf("opening ptmx (master) device\n");
    printf("opened ptmx (master) device: %d\n", ptm);
    printf("opening %s (slave) device\n", devname);
    printf("opened %s (slave) device: %d\n", devname, pts);
    printf("duping current stdin (fd 0), stdout (fd 1), and stderr (fd 2) to %s (slave) device: %d\n", devname, pts);
    printf("returning ptmx (master) device: %d\n", ptm);
    LOGV("returning ptmx (master) device: %d\n", ptm);
    return ptm;
}
