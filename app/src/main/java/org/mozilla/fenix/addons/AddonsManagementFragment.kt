/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.addons

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontStyle.FONT_WEIGHT_MEDIUM
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.forkmaintainers.iceraven.components.PagedAddonInstallationDialogFragment
import io.github.forkmaintainers.iceraven.components.PagedAddonsManagerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.webextension.WebExtensionInstallException
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManagerException
import mozilla.components.feature.addons.ui.translateName
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.view.hideKeyboard
import org.mozilla.fenix.BuildConfig
import org.mozilla.fenix.Config
import org.mozilla.fenix.R
import org.mozilla.fenix.components.FenixSnackbar
import org.mozilla.fenix.databinding.FragmentAddOnsManagementBinding
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.getRootView
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.runIfFragmentIsAttached
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.extension.WebExtensionPromptFeature
import org.mozilla.fenix.theme.ThemeManager
import java.util.concurrent.CancellationException
import java.util.Locale

/**
 * Fragment use for managing add-ons.
 */
@Suppress("TooManyFunctions", "LargeClass")
class AddonsManagementFragment : Fragment(R.layout.fragment_add_ons_management) {

    private val logger = Logger("AddonsManagementFragment")

    private val args by navArgs<AddonsManagementFragmentArgs>()

    private var binding: FragmentAddOnsManagementBinding? = null

    private val webExtensionPromptFeature = ViewBoundFeatureWrapper<WebExtensionPromptFeature>()

    /**
     * Whether or not an add-on installation is in progress.
     */
    private var isInstallationInProgress = false

    private var installExternalAddonComplete: Boolean
        set(value) {
            arguments?.putBoolean(BUNDLE_KEY_INSTALL_EXTERNAL_ADDON_COMPLETE, value)
        }
        get() {
            return arguments?.getBoolean(BUNDLE_KEY_INSTALL_EXTERNAL_ADDON_COMPLETE, false) ?: false
        }

    private var adapter: PagedAddonsManagerAdapter? = null

    // We must save the add-on list in the class, or we won't have it
    // downloaded for the non-suspending search function
    private var addons: List<Addon>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logger.info("View created for AddonsManagementFragment")
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentAddOnsManagementBinding.bind(view)
        bindRecyclerView()
        setupMenu()
        webExtensionPromptFeature.set(
            feature = WebExtensionPromptFeature(
                store = requireComponents.core.store,
                provideAddons = { addons!! },
                context = requireContext(),
                fragmentManager = parentFragmentManager,
                view = view,
                onAddonChanged = {
                    runIfFragmentIsAttached {
                        adapter?.updateAddon(it)
                    }
                },
            ),
            owner = this,
            view = view,
        )
    }


    private fun setupMenu() {
        val menuHost = requireActivity() as MenuHost

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                    inflater.inflate(R.menu.addons_menu, menu)
                    val searchItem = menu.findItem(R.id.search)
                    val searchView: SearchView = searchItem.actionView as SearchView
                    searchView.imeOptions = EditorInfo.IME_ACTION_DONE
                    searchView.queryHint = getString(R.string.addons_search_hint)

                    searchView.setOnQueryTextListener(
                        object : SearchView.OnQueryTextListener {
                            override fun onQueryTextSubmit(query: String): Boolean {
                                searchAddons(query.trim())
                                return false
                            }

                            override fun onQueryTextChange(newText: String): Boolean {
                                searchAddons(newText.trim())
                                return false
                            }
                        },
                    )
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    // Handle the menu selection
                    return true
                }
            },
            viewLifecycleOwner, Lifecycle.State.RESUMED,
        )
    }

    private fun searchAddons(addonSearchText: String): Boolean {
        if (adapter == null) {
            return false
        }

        val searchedAddons = arrayListOf<Addon>()
        addons?.forEach { addon ->
            val names = addon.translatableName
            val language = Locale.getDefault().language
            names[language]?.let { name ->
                if (name.lowercase().contains(addonSearchText.lowercase())) {
                    searchedAddons.add(addon)
                }
            }
            val description = addon.translatableDescription
            description[language]?.let { desc ->
                if (desc.lowercase().contains(addonSearchText.lowercase())) {
                    if (!searchedAddons.contains(addon)) {
                        searchedAddons.add(addon)
                    }
                }
            }
        }
        updateUI(searchedAddons)

        return true
    }

    private fun updateUI(searchedAddons: List<Addon>) {
        adapter?.updateAddons(searchedAddons)

        if (searchedAddons.isEmpty()) {
            binding?.addOnsEmptyMessage?.visibility = View.VISIBLE
            binding?.addOnsList?.visibility = View.GONE
        } else {
            binding?.addOnsEmptyMessage?.visibility = View.GONE
            binding?.addOnsList?.visibility = View.VISIBLE
        }
    }


    override fun onResume() {
        logger.info("Resumed AddonsManagementFragment")

        super.onResume()
        showToolbar(getString(R.string.preferences_addons))
        view?.hideKeyboard()
    }

    override fun onDestroyView() {
        logger.info("Destroyed view for AddonsManagementFragment")

        super.onDestroyView()
        // letting go of the resources to avoid memory leak.
        adapter = null
        binding = null
    }

    private fun bindRecyclerView() {
        logger.info("Binding recycler view for AddonsManagementFragment")

        val managementView = AddonsManagementView(
            navController = findNavController(),
            onInstallButtonClicked = ::installAddon,
        )

        val recyclerView = binding?.addOnsList
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        val shouldRefresh = adapter != null

        logger.info("AddonsManagementFragment should refresh? $shouldRefresh")

        // If the fragment was launched to install an "external" add-on from AMO, we deactivate
        // the cache to get the most up-to-date list of add-ons to match against.
        val allowCache = args.installAddonId == null || installExternalAddonComplete
        lifecycleScope.launch(IO) {
            try {
                logger.info("AddonsManagementFragment asking for addons")

                addons = requireContext().components.addonManager.getAddons(allowCache = allowCache)
                lifecycleScope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        if (!shouldRefresh) {
                            adapter = PagedAddonsManagerAdapter(
                                requireContext().components.addonCollectionProvider,
                                managementView,
                                addons!!,
                                style = createAddonStyle(requireContext()),
                            )
                        }
                        isInstallationInProgress = false
                        binding?.addOnsProgressBar?.isVisible = false
                        binding?.addOnsEmptyMessage?.isVisible = false

                        recyclerView?.adapter = adapter
                        if (shouldRefresh) {
                            adapter?.updateAddons(addons!!)
                        }

                        args.installAddonId?.let { addonIn ->
                            if (!installExternalAddonComplete) {
                                installExternalAddon(addons!!, addonIn)
                            }
                        }
                    }
                }
            } catch (e: AddonManagerException) {
                lifecycleScope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        binding?.let {
                            showSnackBar(it.root, getString(R.string.mozac_feature_addons_failed_to_query_add_ons))
                        }
                        isInstallationInProgress = false
                        binding?.addOnsProgressBar?.isVisible = false
                        binding?.addOnsEmptyMessage?.isVisible = true
                    }
                }
            }
        }
    }

    @VisibleForTesting
    internal fun installExternalAddon(supportedAddons: List<Addon>, installAddonId: String) {
        val addonToInstall = supportedAddons.find { it.downloadId == installAddonId }
        if (addonToInstall == null) {
            showErrorSnackBar(getString(R.string.addon_not_supported_error))
        } else {
            if (addonToInstall.isInstalled()) {
                showErrorSnackBar(getString(R.string.addon_already_installed))
            } else {
                installAddon(addonToInstall)
            }
        }
        installExternalAddonComplete = true
    }

    @VisibleForTesting
    internal fun showErrorSnackBar(text: String) {
        runIfFragmentIsAttached {
            view?.let {
                showSnackBar(it, text, FenixSnackbar.LENGTH_LONG)
            }
        }
    }

    private fun createAddonStyle(context: Context): PagedAddonsManagerAdapter.Style {
        val sectionsTypeFace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Typeface.create(Typeface.DEFAULT, FONT_WEIGHT_MEDIUM, false)
        } else {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        return PagedAddonsManagerAdapter.Style(
            sectionsTextColor = ThemeManager.resolveAttribute(R.attr.textPrimary, context),
            addonNameTextColor = ThemeManager.resolveAttribute(R.attr.textPrimary, context),
            addonSummaryTextColor = ThemeManager.resolveAttribute(R.attr.textSecondary, context),
            sectionsTypeFace = sectionsTypeFace,
            addonAllowPrivateBrowsingLabelDrawableRes = R.drawable.ic_add_on_private_browsing_label,
        )
    }

    internal fun installAddon(addon: Addon) {
        requireContext().components.addonManager.installAddon(
            addon,
            onSuccess = {
                runIfFragmentIsAttached {
                    isInstallationInProgress = false
                    adapter?.updateAddon(it)
                }
            },
            onError = { _, e ->
                this@AddonsManagementFragment.view?.let { view ->
                    // No need to display an error message if installation was cancelled by the user.
                    if (e !is CancellationException && e !is WebExtensionInstallException.UserCancelled) {
                        val rootView = activity?.getRootView() ?: view
                        context?.let {
                            showSnackBar(
                                rootView,
                                getString(
                                    R.string.mozac_feature_addons_failed_to_install,
                                    addon.translateName(it),
                                ),
                            )
                        }
                    }
                    isInstallationInProgress = false
                }
            },
        )
    }

    companion object {
        private const val BUNDLE_KEY_INSTALL_EXTERNAL_ADDON_COMPLETE = "INSTALL_EXTERNAL_ADDON_COMPLETE"
    }
}
