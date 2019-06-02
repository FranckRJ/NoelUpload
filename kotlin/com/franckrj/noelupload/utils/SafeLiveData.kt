package com.franckrj.noelupload.utils

import androidx.lifecycle.LiveData

/**
 * Une [LiveData] qui ne peut pas contenir null.
 */
open class SafeLiveData<T : Any>(newVal: T) : LiveData<T>(newVal) {
    override fun getValue(): T {
        return super.getValue()!!
    }
}
