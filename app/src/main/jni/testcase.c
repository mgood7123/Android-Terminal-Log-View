//
// Created by Mac on 1/5/20.
//

#include <dirent.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>
#include <stdbool.h>

int columns = 80;
int rows = 80;

int main() {
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) {
        puts("Cannot open /dev/ptmx");
        return -1;
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
        puts("Cannot grantpt()/unlockpt()/ptsname_r() on /dev/ptmx");
        return -1;
    }

    // Enable UTF-8 mode.
    struct termios tios;
    tcgetattr(ptm, &tios);
    tios.c_iflag |= IUTF8;
    tcsetattr(ptm, TCSANOW, &tios);

    /** Set initial winsize. */
    struct winsize sz = { .ws_row = rows, .ws_col = columns };
    ioctl(ptm, TIOCSWINSZ, &sz);

    // Clear signals which the Android java process may have blocked:
    sigset_t signals_to_unblock;
    sigfillset(&signals_to_unblock);
    sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);
    setsid();

    int pts = open(devname, O_RDWR);
    if (pts < 0) {
        puts("failed to open slave device belonging to /dev/ptmx");
        return -1;
    }

    fprintf(stdout, "i have put data into stdout, but i have not put data into stderr\n");

    dup2(pts, 0);
    dup2(pts, 1);
    dup2(pts, 2);

    fprintf(stdout, "print to stdout\n");
    fprintf(stderr, "print to stderr\n");

    puts("Welcome to the Android Terminal Log\n");

    puts("To view a demonstration long press on the screen");
    puts("tap \"More...\"");
    puts("tap \"printf something to the terminal");
    puts("\nBy default this invokes the following C/C++ code:\n");
    puts("    printf(\"HELLO FROM NATIVE CPP\\n\");\n");
    puts("like a terminal, output is sent by printing a new line");
    puts("so if no output appears, try putting a new line in your printing function");
    puts("\nNow printing logging info:\n");

    printf("opening ptmx (master) device\n");
    printf("opened ptmx (master) device: %d\n", ptm);
    printf("opening %s (slave) device\n", devname);
    printf("opened %s (slave) device: %d\n", devname, pts);
    printf(
        "duping current stdin (fd 0), stdout (fd 1), and stderr (fd 2) to %s (slave) device: %d\n",
        devname, pts);
    printf("returning ptmx (master) device: %d\n", ptm);
    puts("press ctrl+c to exit...");
    puts("now printing buffers from ptm device...");

    // now... read from ptm...
    // this will be difficult due to this process's stdout being redirected to the pts
    // so we shall write the output to a file instead

    char ch[1];
    bool newLine = true;
    int FD = open("FILE", O_CREAT|O_TRUNC|O_RDWR|O_APPEND, 777);
    while(1) {
        int r = read(ptm, ch, 1);
        if (r != -1 && r != 0) {
            if (newLine) {
                char buffer[4096];
                for (int i = 0; i != 4097; i++) buffer[i] = '\0';
                size_t elements = (size_t) sprintf(buffer, "r = %d, ptm buffer: ", r);
                write(FD, buffer, elements);
                newLine = false;
            }
            if (ch[0] == '\n') {
                newLine = true;
            } else {
                write(FD, ch, 1);
            }
        }
    }
    // never returns
}
