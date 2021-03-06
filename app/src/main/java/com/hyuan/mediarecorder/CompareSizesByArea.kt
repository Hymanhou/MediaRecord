package com.hyuan.mediarecorder

import android.util.Size
import java.lang.Long.signum

class CompareSizesByArea: Comparator<Size> {

    override fun compare(o1: Size, o2: Size): Int {
        return signum(o1.width.toLong() * o1.height - o2.width.toLong() * o2.height)
    }
}