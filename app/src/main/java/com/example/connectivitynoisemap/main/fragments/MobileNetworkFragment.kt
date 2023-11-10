package com.example.connectivitynoisemap.main.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import com.example.connectivitynoisemap.MeasurementApplication
import com.example.connectivitynoisemap.data.MeasurementViewModel
import com.example.connectivitynoisemap.data.MeasurementViewModelFactory
import com.example.connectivitynoisemap.data.type.DataType
import com.example.connectivitynoisemap.databinding.FragmentMobileNetworkBinding
import com.example.connectivitynoisemap.main.MainActivity
import com.example.connectivitynoisemap.main.fragments.viewmodels.FragmentViewModelFactory
import com.example.connectivitynoisemap.main.fragments.viewmodels.MobileNetworkViewModel
import com.example.connectivitynoisemap.main.interfaces.MeasurementFragmentInterface
import com.example.connectivitynoisemap.main.module.implementation.MapHandler
import com.example.connectivitynoisemap.main.module.implementation.OnMapLoaded
import com.example.connectivitynoisemap.main.utils.GUI
import com.example.connectivitynoisemap.main.utils.MapUtils.Companion.getGridSquare
import com.example.connectivitynoisemap.main.utils.MapUtils.Companion.latLngToMgrs
import com.example.connectivitynoisemap.main.utils.ValueClass
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import mil.nga.mgrs.MGRS

class MobileNetworkFragment :
    Fragment(),
    MeasurementFragmentInterface,
    OnMapLoaded
{

    private var _binding: FragmentMobileNetworkBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val measurementViewModel: MeasurementViewModel
        by viewModels {
            MeasurementViewModelFactory(
                (activity.application as MeasurementApplication).repository,
                dataType
            )
        }
    private val fragmentViewModel: MobileNetworkViewModel
        by viewModels{
            FragmentViewModelFactory()
        }
    private val mapHandler: MapHandler by lazy {
        activity.mapHandlerViewModel.mapHandler
    }
    private val currentLatLng: LiveData<LatLng>
    by lazy { mapHandler.currentLatLng }

    private val currentGridSquare : Map<String, MGRS>
        get() = getGridSquare(latLngToMgrs(currentLatLng.value!!))

    private val isLocationPermGranted: Boolean
        get() = activity.isLocationPermGranted

    override val activity: MainActivity
        get() = requireActivity() as MainActivity

    override val dataType: DataType = DataType.MOBILE_NETWORK

    // METHODS

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (!isLocationPermGranted) {
            locationPermission()
        }
        fragmentViewModel.lateInit(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMobileNetworkBinding.inflate(inflater, container, false)

        // Set onMapReady() called in MapHandler Listener
        mapHandler.setOnMapLoadedListener(this)
        // set the mapView of the MapHandler
        val mapView = binding.mapViewContainer.mapView
        mapHandler.setMapView(mapView, savedInstanceState)
        /*
        currentLatLng.observe(viewLifecycleOwner){ latLng ->
            if(latLng != null){
                val gridSquare = MapUtils.getGridSquare(MapUtils.latLngToMgrs(latLng))
                val stateData = activity.getButtonState(dataType, gridSquare)
                stateData.observe(viewLifecycleOwner){ state ->
                    if(activity.isBgOperationEnabledSP) {
                        if (state)
                            this.measureValue()
                    }else
                        activity.enableActionBtn(state)
                }
            }
        }

         */

        /*
        if (currentLatLng.value != null) {
            val stateData = activity.getButtonState(dataType, currentGridSquare)
            stateData.observe(viewLifecycleOwner) { state ->
                if(activity.isBgOperationEnabledSP) {
                    if (state)
                        this.measureValue()
                }else
                    activity.enableActionBtn(state)
            }
        }
         */


        /*
        // Enable the action button or wait for the timer to finish
        if (buttonState.value!!)
            activity.enableActionBtn(true)
        else{
            activity.enableActionBtn(false)
        }
        buttonState.observe(viewLifecycleOwner) { state ->
            if (state == true)
                activity.enableActionBtn(true)
        }
         */

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Show the action button if background operations are disabled
        if(!activity.isBgOperationEnabledSP)
            activity.showActionBtn(true)
    }

    override fun onResume() {
        super.onResume()
        //mapHandler.onFragmentResumed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        fragmentViewModel.removeListener()
    }

    override fun onLocationPermGranted() {
        mapHandler.onLocationPermGranted()
    }

    override fun locationPermission(){
        activity.requestLocationPermission()
    }

    override fun onMapLoaded() {
        // Draw the map squares for this fragment and data type
        mapHandler.drawMapSquares(dataType)
    }

    override fun measureValue(){

        // Mobile Network signal strength measurement
        val mobileRssi = fragmentViewModel.mobileRssi
        if(mobileRssi == 1.0)
            GUI.showToast(requireContext(),"Enable Mobile Network on your device")
        else{
            val latLngData = currentLatLng

            latLngData.observe(viewLifecycleOwner) { latLng ->
                if(latLng != null) {
                    latLngData.removeObservers(viewLifecycleOwner)

                    lifecycleScope.launch {

                        val gridSquare = getGridSquare(latLngToMgrs(latLng))
                        // Temporarily disable the action button
                        activity.tempDisableActionBtn(
                            dataType,
                            gridSquare
                        )

                        val remainingMeasurements =
                            measurementViewModel.saveData(
                                mobileRssi,
                                latLng,
                                activity.numMeasurementsSP
                            )
                        if(remainingMeasurements > 0){
                            GUI.showToast(
                                requireContext(),
                                "$remainingMeasurements measurements remaining",
                                Toast.LENGTH_SHORT
                            )
                        } else { // remainingMeasurements <= 0
                            // Process the data inserted
                            GUI.showToast(
                                requireContext(),
                                "Measurement completed",
                                Toast.LENGTH_SHORT
                            )
                            val avgValue =
                                if(remainingMeasurements == 0)
                                    // Process the data in order to create the map square
                                    // for the first time in that grid square
                                    measurementViewModel.processDataAndCreateSquare(
                                        gridSquare,
                                        activity.numMeasurementsSP
                                    )
                                else
                                    // map square already exists: already processed data inserted
                                    measurementViewModel.getAvgValue(
                                        gridSquare,
                                        activity.numMeasurementsSP
                                    )

                            val signalClass =
                                ValueClass.fromValueToClass(dataType, avgValue)

                            mapHandler.addOrUpdateMapSquare(dataType, gridSquare, signalClass)
                        }
                    }
                }
            }
        }
    }

    /*
    fun enableMobNetAlert(){
        val builder = AlertDialog.Builder(requireContext())

        builder.setTitle("Enable Mobile Network")

        builder.setMessage("If you want to use this feature of the app you need to enable Mobile Network")

        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
            //(requireActivity() as MainActivity).findNavController(R.id.nav_host_fragment).navigate(R.id.action_mobileNetworkFragment_to_homeFragment)
        }

        val alertDialog = builder.create()

        alertDialog.show()
    }
     */

}


