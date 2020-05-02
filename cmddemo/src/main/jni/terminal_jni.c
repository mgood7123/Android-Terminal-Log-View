#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>
#include <stdlib.h>
#include <termios.h>
#include <string.h>
#include <errno.h>
#include <stdbool.h>

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
Java_alpine_term_TerminalClientAPI_getPid(JNIEnv *ALPINE_TERM_UNUSED(env), jclass ALPINE_TERM_UNUSED(clazz)) {
    return getpid();
}

jintArray createJniArray(JNIEnv *env, size_t size) {
    jintArray result = (*env)->NewIntArray(env, size);
    if (result == NULL) {
        return NULL; /* out of memory error thrown */
    } else return result;
}

void setJniArrayIndex(JNIEnv *env, jintArray * array, int index, int value) {
    // fill a temp structure to use to populate the java int array
    jint fill[1];

    // populate the values
    fill[0] = value;

    // move from the temp structure to the java structure
    (*env)->SetIntArrayRegion(env, *array, index, 1, fill);
}

bool setJniArrayIndexes(
    JNIEnv *env, jintArray * array, int index,
    int * pointer, int totalIndexesInPointer
) {
    // fill a temp structure to use to populate the java int array
    jint * fill = (jint*) malloc(totalIndexesInPointer * sizeof(jint));
    if (fill == NULL) return false;

    // populate the values
    // if valueTotalIndexes is 1, then
    for (int i = 0; i < totalIndexesInPointer; ++i) {
        fill[i] = pointer[i];
    }

    // move from the temp structure to the java structure
    (*env)->SetIntArrayRegion(env, *array, index, totalIndexesInPointer, fill);
    free(fill);
    return true;
}

JNIEXPORT jintArray JNICALL
Java_alpine_term_TerminalClientAPI_createPseudoTerminal(
    JNIEnv *env,
    jclass ALPINE_TERM_UNUSED(clazz)
) {
    LOGV("opening ptmx (master) device");
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    LOGV("opened ptmx (master) device: %d", ptm);
    if (ptm < 0) {
        jintArray a = createJniArray(env, 1);
        setJniArrayIndex(env, &a, 0, throw_runtime_exception(env, "Cannot open /dev/ptmx"));
        return a;
    }

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
        char * msg = "Cannot grantpt()/unlockpt()/ptsname_r() on /dev/ptmx";
        jintArray a = createJniArray(env, 1);
        setJniArrayIndex(env, &a, 0, throw_runtime_exception(env, msg));
        return a;
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
        char * msg = "failed to open slave device";
        jintArray a = createJniArray(env, 1);
        setJniArrayIndex(env, &a, 0, throw_runtime_exception(env, msg));
        return a;
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
    jintArray a = createJniArray(env, 2);
    setJniArrayIndex(env, &a, 0, ptm);
    setJniArrayIndex(env, &a, 1, pts);
    return a;
}
