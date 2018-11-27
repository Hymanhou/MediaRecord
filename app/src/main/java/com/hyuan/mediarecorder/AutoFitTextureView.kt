package com.hyuan.mediarecorder

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import java.lang.IllegalArgumentException

class AutoFitTextureView : TextureView {

    private var mRatioWidth: Int = 0
    private var mRatioHeight: Int = 0

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0):super(context,attrs,defStyle) {

    }

    fun setAspectRatio(width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Size cannot be negative")
        }
        mRatioHeight = height
        mRatioWidth = width
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = View.MeasureSpec.getSize(widthMeasureSpec);
        val height = View.MeasureSpec.getSize(heightMeasureSpec);

        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height)
        } else {
            //发生在屏幕旋转，当前宽或者高是最匹配屏幕尺寸的，检查缩放后是否会导致尺寸变大，变大会导致超出屏幕
            //一边的比例变大，那么另一边的比例必然减少，所以保证比例变大的边尺寸不变，另一边尺寸必然减少,两边都不会超出屏幕
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight/mRatioWidth)
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }
}