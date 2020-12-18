package com.example.maps

interface IOnLoadLocationListener {
    fun onLocationLoadSuccess(LatLangs:List<MyLatLang>)
    fun onLocatoinLoadFailed(message:String)
}