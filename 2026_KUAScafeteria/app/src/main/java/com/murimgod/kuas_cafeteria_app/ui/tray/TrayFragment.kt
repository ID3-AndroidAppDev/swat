package com.murimgod.kuas_cafeteria_app.ui.tray

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.murimgod.kuas_cafeteria_app.databinding.FragmentTrayBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TrayFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentTrayBinding? = null
    private val binding get() = _binding!!

    private val trayViewModel: TrayViewModel by activityViewModels()
    private lateinit var adapter: TrayAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TrayAdapter(
            onIncrease = { trayViewModel.increaseQty(it) },
            onDecrease = { trayViewModel.decreaseQty(it) }
        )
        binding.rvTrayItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrayItems.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            trayViewModel.entries.collectLatest { entries ->
                adapter.submitList(entries)
                val kcal = trayViewModel.totalKcal.toInt()
                val price = trayViewModel.totalPrice
                binding.tvTotalKcalPrice.text = "$kcal kcal · ¥$price"
                val p = trayViewModel.totalProtein
                val f = trayViewModel.totalFat
                if (p > 0 || f > 0) {
                    binding.tvTotalMacros.text = "P %.1fg · F %.1fg".format(p, f)
                    binding.tvTotalMacros.visibility = View.VISIBLE
                } else {
                    binding.tvTotalMacros.visibility = View.GONE
                }
            }
        }

        binding.btnClearTray.setOnClickListener { trayViewModel.clear() }
        binding.btnDone.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TrayFragment"
    }
}
