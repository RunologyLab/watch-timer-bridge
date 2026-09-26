package android.support.wearable.complications;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** Minimal wire representation of the standard Wear OS time-difference text. */
public final class ComplicationText implements Parcelable {
    private final long endTimeMillis;
    private final String plain;

    public ComplicationText(long endTimeMillis) {
        this.endTimeMillis = endTimeMillis;
        this.plain = null;
    }

    public ComplicationText(String plain) {
        this.endTimeMillis = 0;
        this.plain = plain;
    }

    private ComplicationText(Parcel parcel) {
        Bundle fields = parcel.readBundle(ComplicationText.class.getClassLoader());
        endTimeMillis = fields.getLong("difference_period_end");
        plain = endTimeMillis == 0 ? String.valueOf(fields.getCharSequence("surrounding_string")) : null;
    }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        Bundle fields = new Bundle();
        fields.putCharSequence("surrounding_string", plain == null ? "^1" : plain);
        if (plain == null) {
            // The reference period is the single deadline instant. If the start
            // were zero, all times before the deadline would lie *inside* the
            // reference period and the displayed difference would be 00:00.
            fields.putLong("difference_period_start", endTimeMillis);
            fields.putLong("difference_period_end", endTimeMillis);
            fields.putInt("difference_style", 1); // numeric stopwatch formatting
            fields.putBoolean("show_now_text", false);
        }
        parcel.writeBundle(fields);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<ComplicationText> CREATOR = new Parcelable.Creator<ComplicationText>() {
        @Override public ComplicationText createFromParcel(Parcel in) { return new ComplicationText(in); }
        @Override public ComplicationText[] newArray(int size) { return new ComplicationText[size]; }
    };
}
