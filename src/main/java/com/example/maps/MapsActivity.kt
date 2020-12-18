package com.example.maps

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.media.MediaPlayer
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.*

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import kotlin.random.Random

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, IOnLoadLocationListener,
    GeoQueryEventListener {
    override fun onLocationLoadSuccess(latLngs: List<MyLatLang>) {

        dangeroudArea = ArrayList()
        for (myLatLng in latLngs)
        {
            val convert =  LatLng(myLatLng.latitude,myLatLng.longitude)
            dangeroudArea!!.add(convert)
        }
        //Calling Map Display
        // Obtain the SupportMapFragment and get notified when the map is ready to be used
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //clear map and add data again
        if (mMap != null)
        {
            mMap!!.clear()
            //Add again user Marker
            addUserMarker()
            //Add Circle of Dangerous Area
            addCircleArea()
        }


    }


    private fun addCircleArea() {
        if (geoQuery != null)
        {
            //Remove old listener, image if you remove an location in firebase
            // it must be remove listenerin geoFire too
            geoQuery!!.removeGeoQueryEventListener(this@MapsActivity)
            geoQuery!!.removeAllListeners()
        }
        //Add again
        for (latLng in dangeroudArea!!)
        {
            mMap!!.addCircle(CircleOptions().center(latLng)
                .radius(500.0) //500m
                .strokeColor(Color.BLUE)
                .fillColor(0x220000FF)
                .strokeWidth(5.0f)
             )
            // create GeoQuery when user in Dangerous Area
            geoQuery = geoFire!!.queryAtLocation(GeoLocation(latLng.latitude,latLng.longitude), 0.5) // 0.5 = 500m
            geoQuery!!.addGeoQueryEventListener(this@MapsActivity)

        }
    }

    override fun onLocatoinLoadFailed(message: String) {
        Toast.makeText(this,""+message,Toast.LENGTH_SHORT).show()
    }

    private var mMap: GoogleMap?= null
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var currentMarker: Marker?= null
    private lateinit var myLocationRef: DatabaseReference
    private lateinit var dangeroudArea: MutableList<LatLng>
    private lateinit var listener: IOnLoadLocationListener

    private lateinit var myCity: DatabaseReference
    private lateinit var lastLocation: Location
    private var geoQuery: GeoQuery?= null
    private lateinit var geoFire: GeoFire

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        //Request Runtime
        Dexter.withActivity(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object: PermissionListener{
                override fun onPermissionGranted(response: PermissionGrantedResponse?) {

                    buildLocationRequest ()
                    buildLocationCallback ()
                    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this@MapsActivity)
                    initArea()
                    settingGeoFire()


                    //Add Dangerous to Firebase
                   addDangerousToFireBase()
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest?,
                    token: PermissionToken?
                ) {

                }

                override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                    Toast.makeText(this@MapsActivity, "You Must Enable this Permission", Toast.LENGTH_SHORT).show()
                }

            }).check()



    }

    private fun addDangerousToFireBase() {
        dangeroudArea = ArrayList()
        dangeroudArea.add(LatLng(19.956068, 73.833845))
        dangeroudArea.add(LatLng(20.006959, 73.791094))
        dangeroudArea.add(LatLng(20.559570, 74.519641))

        //Submitting this list to firebase

        FirebaseDatabase.getInstance()
            .getReference("DangerousArea")
            .child("MyCity")
            .setValue(dangeroudArea)
            .addOnCompleteListener { Toast.makeText(this@MapsActivity,"Update",Toast.LENGTH_SHORT).show()

            }.addOnFailureListener { ex -> Toast.makeText(this@MapsActivity,""+ex.message,Toast.LENGTH_SHORT).show() }
    }

    private fun settingGeoFire() {
        myLocationRef = FirebaseDatabase.getInstance().getReference("MyLocation")
        geoFire = GeoFire(myLocationRef)
    }


    private fun initArea() {
        myCity = FirebaseDatabase.getInstance()
            .getReference("DangerousArea")
            .child("myCity")

        listener = this

        myCity!!.addValueEventListener(object:ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {

            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Update Dangerous Area
                val latLngList = ArrayList<MyLatLang>()
                for (locationSnapShot in dataSnapshot.children)
                {
                    val latLng = locationSnapShot.getValue(MyLatLang::class.java)
                    latLngList.add(latLng!!)
                }
                listener!!.onLocationLoadSuccess(latLngList)
            }

        })

    }

    private fun buildLocationCallback() {
        locationCallback = object : LocationCallback () {
            //Ctrl+o
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                if(mMap != null)
                {
                    lastLocation = locationResult!!.lastLocation
                    addUserMarker()
                }
            }
        }
    }

    private fun addUserMarker() {
        geoFire!!.setLocation("You", GeoLocation(lastLocation!!.latitude,
            lastLocation!!.longitude)) {_,_ ->
            if (currentMarker != null) currentMarker!!.remove()
            currentMarker = mMap!!.addMarker(MarkerOptions().position(LatLng(lastLocation!!.latitude,
                lastLocation!!.longitude))
                .title("You"))
            //After add marker, move camera
            mMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(currentMarker!!.position,12.0f))
        }
    }

    private fun buildLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest!!.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        locationRequest!!.interval = 5000
        locationRequest!!.fastestInterval = 3000
        locationRequest!!.smallestDisplacement = 10f

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap!!.uiSettings.isZoomControlsEnabled = true

        if (fusedLocationProviderClient != null)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    return
            }
            fusedLocationProviderClient!!.requestLocationUpdates(locationRequest, locationCallback!!, Looper.myLooper())

            addCircleArea()
        }
    }

    override fun onStop() {
        fusedLocationProviderClient!!.removeLocationUpdates(locationCallback!!)
        super.onStop()
    }

    override fun onGeoQueryReady() {

    }

    override fun onKeyEntered(key: String?, location: GeoLocation?) {
        sendNotification("Sameer Rathod", String.format("%s entered the dangerous area",key))
    }

    private fun sendNotification(title: String, content: String) {
        Toast.makeText(this,""+content,Toast.LENGTH_SHORT).show()

        val NOTIFICATION_CHANNEL_ID = "sameer_multiple_location"
        val NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MyNotification", NotificationManager.importance
            )

            //Config
            notificationChannel.description = "Channel Description"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.RED
            notificationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            notificationChannel.enableVibration(true)

            NotificationManager.createNotificationChannel(notificationChannel)
            // NotificationManager is having small n in video



            var builder = NotificationCompat.Builder(this,NOTIFICATION_CHANNEL_ID)
            builder.setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(false)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(resources,R.mipmap.ic_launcher))

            val notification =builder.build()
            NotificationManager.notify(java.util.Random().nextInt(),notification)
            // NotificationManager is having small n in video

        }
    }

    override fun onKeyMoved(key: String?, location: GeoLocation?) {
        sendNotification("Sameer Rathod", String.format("%s move within the dangerous area",key))
    }

    override fun onKeyExited(key: String?) {
        sendNotification("Sameer Rathod", String.format("%s leave the dangerous area",key))
    }

    override fun onGeoQueryError(error: DatabaseError?) {
        Toast.makeText(this,""+error!!.message,Toast.LENGTH_SHORT).show()
    }

}