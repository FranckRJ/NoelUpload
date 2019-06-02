package com.franckrj.noelupload.utils

class SafeMutableLiveData<T : Any>(newVal: T) : SafeLiveData<T>(newVal) {
    public override fun postValue(value: T) {
        super.postValue(value)
    }

    public override fun setValue(value: T) {
        super.setValue(value)
    }

    fun updateValue() {
        value = value
    }
}
