package com.example.connectivitynoisemap.main.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.connectivitynoisemap.MeasurementApplication
import com.example.connectivitynoisemap.data.MeasurementViewModel
import com.example.connectivitynoisemap.data.MeasurementViewModelFactory
import com.example.connectivitynoisemap.data.type.DataType
import com.example.connectivitynoisemap.databinding.FragmentWifiBinding
import com.example.connectivitynoisemap.main.MainActivity
import com.example.connectivitynoisemap.main.fragments.viewmodels.FragmentViewModelFactory
import com.example.connectivitynoisemap.main.fragments.viewmodels.WifiViewModel
import com.example.connectivitynoisemap.main.interfaces.MeasurementFragmentInterface
import com.example.connectivitynoisemap.main.module.implementation.MapHandler
import com.example.connectivitynoisemap.main.module.implementation.MapHandlerViewModel
import com.example.connectivitynoisemap.main.module.implementation.OnMapLoaded
import com.example.connectivitynoisemap.main.utils.GUI
import com.example.connectivitynoisemap.main.utils.ValueClass
import kotlinx.coroutines.launch


class WifiFragment :
    Fragment(),
    MeasurementFragmentInterface,
    OnMapLoaded
{

    private var _binding: FragmentWifiBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val measurementViewModel: MeasurementViewModel
        by viewModels {
            MeasurementViewModelFactory(
                (activity.application as MeasurementApplication).repository,
                dataType
            )
        }
    private val fragmentViewModel: WifiViewModel
        by viewModels{
            FragmentViewModelFactory()
        }

    private val mapHandler: MapHandler by lazy {
        ViewModelProvider(activity)[MapHandlerViewModel::class.java].mapHandler
    }

    private val dataType: DataType = DataType.WIFI
    private val isLocationPermGranted: Boolean
        get() = activity.isLocationPermGranted
    private val buttonState
        get() = activity.buttonState

    override val activity: MainActivity
        get() = requireActivity() as MainActivity

    override val fId: Int by lazy { dataType.ordinal }

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
        _binding = FragmentWifiBinding.inflate(inflater, container, false)

        // Clear the map from all the squares if the map is ready
        /*
        if(mapHandler.isMapReady) {
            mapHandler.drawMapSquares(dataType)
        }else
        */
        mapHandler.setOnMapLoadedListener(this)

        // Set the mapView of the MapHandler
        val mapView = binding.mapViewContainer.mapView
        mapHandler.setMapView(mapView, savedInstanceState)


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

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Show the action button
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

    override fun onLocationPermGranted() {
        mapHandler.onLocationPermGranted()
    }

    override fun locationPermission() {
        activity.requestLocationPermission()
    }

    override fun onMapLoaded() {
        // Draw the map squares for this fragment and data type
        mapHandler.drawMapSquares(dataType)
    }

    override fun measureValue() {
        // Temporarily disable the action button
        activity.tempDisableActionBtn(fId)
        activity.enableActionBtn(false)

        // Wifi signal strength measurement
        val wifiRssi = fragmentViewModel.wifiRssi
        when (wifiRssi) {
            +1.0-> {
                Log.e("ERROR", "Error in wifi connection RSSI")
            }
            0.0 -> {
                GUI.showToast(requireContext(), "Enable Wi-Fi on your device")
            }
            else -> {
                val latLngData = mapHandler.currentLatLng

                latLngData.observe(viewLifecycleOwner) { latLng ->
                    if(latLng != null) {
                        latLngData.removeObservers(viewLifecycleOwner)

                        lifecycleScope.launch {
                            val (remainingMeasurements, gridSquare) =
                                measurementViewModel.saveData(
                                    wifiRssi,
                                    latLng,
                                    activity.minNumMeasurementsSharedPref
                                )
                            if(remainingMeasurements > 0){
                                GUI.showToast(
                                    requireContext(),
                                    "$remainingMeasurements measurements remaining",
                                    Toast.LENGTH_SHORT
                                )
                            }
                            else{
                                // Process the data inserted
                                GUI.showToast(
                                    requireContext(),
                                    "Processing Data...",
                                    Toast.LENGTH_SHORT
                                )
                                val avgValue =
                                    if(remainingMeasurements == 0)
                                    // Process the data in order to create the map square
                                    // for the first time in that grid square
                                        measurementViewModel.processDataAndCreateSquare(gridSquare)
                                    else
                                    // map square already exists: already processed data inserted
                                        measurementViewModel.getAvgValue(gridSquare)

                                val signalClass =
                                    ValueClass.fromValueToClass(dataType, avgValue)

                                mapHandler.addOrUpdateMapSquare(dataType, gridSquare, signalClass)
                            }
                        }
                    }
                }
            }
        }
    }

    /*
    private fun enableWifiAlert() {
        val builder = AlertDialog.Builder(requireContext())

        builder.setTitle("Enable WiFi")

        builder.setMessage("If you want to use this feature of the app you need to enable WiFi")

        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
            //view?.findNavController()?.navigate(R.id.action_wifiFragment_to_homeFragment)
        }

        val alertDialog = builder.create()

        alertDialog.show()
    }
    */

}