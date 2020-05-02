package alpine.term;

import android.content.pm.ActivityInfo;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * generated via <a href="http://www.parcelabler.com/">http://www.parcelabler.com/</a>
 */
@SuppressWarnings("WeakerAccess")
public class TrackedActivity implements Parcelable {
    TrackedActivity() {}
    public ActivityInfo activityInfo = null;
    public String packageName = "<INVALID>";
    public String label = "";
    public int pid = 0;
    public ParcelFileDescriptor pseudoTerminal = null;
    public String description;

    /**
     * this wraps a raw native fd into a java {@link FileDescriptor}
     * @param fd a raw native fd
     * @return a valid FileDescriptor, otherwise null
     */
    public static FileDescriptor wrapFileDescriptor(int fd) {
        FileDescriptor result = new FileDescriptor();
        try {
            Field descriptorField;
            try {
                descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
            } catch (NoSuchFieldException e) {
                // For desktop java:
                descriptorField = FileDescriptor.class.getDeclaredField("fd");
            }
            boolean originalAccessibility = descriptorField.isAccessible();
            descriptorField.setAccessible(true);
            descriptorField.set(result, fd);
            descriptorField.setAccessible(originalAccessibility);
        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
            Log.wtf(
                "wrapFileDescriptor",
                "error accessing FileDescriptor#descriptor private field", e
            );
            return null;
        }
        return result;
    }

    /**
     * this unwraps a java {@link FileDescriptor} into a raw native fd
     * @param fileDescriptor a java {@link FileDescriptor}
     * @return a raw native fd
     */
    public static int unwrapFileDescriptor(FileDescriptor fileDescriptor) {
        int fd = -1;
        try {
            Field descriptorField;
            try {
                descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
            } catch (NoSuchFieldException e) {
                // For desktop java:
                descriptorField = FileDescriptor.class.getDeclaredField("fd");
            }
            boolean originalAccessibility = descriptorField.isAccessible();
            descriptorField.setAccessible(true);
            fd = descriptorField.getInt(fileDescriptor);
            descriptorField.setAccessible(originalAccessibility);
        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
            Log.wtf(
                "unwrapFileDescriptor",
                "error accessing FileDescriptor#descriptor private field", e
            );
            return -1;
        }
        return fd;
    }

    /**
     * stores an fd, this can be obtained via getNativeFD() or
     * unwrapFileDescriptor(getJavaFileDescriptor())
     * @return true if the fd was stored successfully, otherwise false
     */
    public boolean storeNativeFD(int fd) {
        // https://github.com/hacking-android/frameworks/blob/943f0b4d46f72532a419fb6171e40d1c93984c8e/devices/google/Pixel%202/29/QPP6.190730.005/src/framework/android/net/IpSecUdpEncapResponse.java#L49
        try {
            FileDescriptor fileDescriptor = wrapFileDescriptor(fd);
            if (fileDescriptor == null) return false;
            pseudoTerminal = ParcelFileDescriptor.dup(fileDescriptor);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * obtains the FileDescriptor stored from storeNativeFD()
     * @return an FileDescriptor, this may be null indicating an error occurred
     */
    public FileDescriptor getJavaFileDescriptor() {
        return pseudoTerminal.getFileDescriptor();
    }

    /**
     * obtains the fd stored from storeNativeFD()
     * @return an fd, this may be -1 indicating an error occurred
     */
    public int getNativeFD() {
        return unwrapFileDescriptor(pseudoTerminal.getFileDescriptor());
    }

    protected TrackedActivity(Parcel in) {
        activityInfo = (ActivityInfo) in.readValue(ActivityInfo.class.getClassLoader());
        packageName = in.readString();
        label = in.readString();
        pid = in.readInt();
        pseudoTerminal = in.readParcelable(ParcelFileDescriptor.class.getClassLoader());
        description = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(activityInfo);
        dest.writeString(packageName);
        dest.writeString(label);
        dest.writeInt(pid);
        dest.writeParcelable(this.pseudoTerminal, 1);
        dest.writeString(description);
    }

    @SuppressWarnings("unused")
    public static final Creator<TrackedActivity> CREATOR = new Creator<TrackedActivity>() {
        @Override
        public TrackedActivity createFromParcel(Parcel in) {
            return new TrackedActivity(in);
        }

        @Override
        public TrackedActivity[] newArray(int size) {
            return new TrackedActivity[size];
        }
    };
}
