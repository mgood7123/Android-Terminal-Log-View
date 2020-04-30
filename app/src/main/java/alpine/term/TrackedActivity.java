package alpine.term;

import android.content.pm.ActivityInfo;
import android.os.Parcel;
import android.os.Parcelable;

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
    public int ptm = 0;
    public int pts = 0;
    public String description;

    protected TrackedActivity(Parcel in) {
        activityInfo = (ActivityInfo) in.readValue(ActivityInfo.class.getClassLoader());
        packageName = in.readString();
        label = in.readString();
        pid = in.readInt();
        ptm = in.readInt();
        pts = in.readInt();
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
        dest.writeInt(ptm);
        dest.writeInt(pts);
        dest.writeString(description);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<TrackedActivity> CREATOR = new Parcelable.Creator<TrackedActivity>() {
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
