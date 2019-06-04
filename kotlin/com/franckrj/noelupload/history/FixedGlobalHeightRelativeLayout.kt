package com.franckrj.noelupload.history

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout

/**
 * Un RelativeLayout dont la hauteur est fixée selon une variable statique.
 */
class FixedGlobalHeightRelativeLayout : RelativeLayout {
    companion object {
        var fixedHeightInPixel: Int = 1
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(fixedHeightInPixel, MeasureSpec.EXACTLY))
    }
}
