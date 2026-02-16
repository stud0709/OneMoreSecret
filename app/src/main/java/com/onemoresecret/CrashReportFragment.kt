package com.onemoresecret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.onemoresecret.composable.CrashReport
import com.onemoresecret.composable.OneMoreSecretTheme
import kotlin.system.exitProcess

class CrashReportFragment : Fragment() {
    private lateinit var crashReportData: CrashReportData

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        crashReportData = requireActivity().intent
            .getSerializableExtra(OmsUncaughtExceptionHandler.EXTRA_CRASH_REPORT) as CrashReportData
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                OneMoreSecretTheme {
                    CrashReport(
                        crashReportData = crashReportData,
                        onDismiss = {
                            requireActivity().finish()
                            exitProcess(0)
                        }
                    )
                }
            }
        }
    }
}