@file:OptIn(InternalReadiumApi::class)

package com.storyteller.reader

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.util.BaseActionModeCallback
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import kotlin.math.ceil

class SelectionActionModeCallback(private val epubView: EpubView) : BaseActionModeCallback() {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        epubView.activity.lifecycleScope.launch {
            val selection = epubView.navigator?.currentSelection() ?: return@launch
            selection.rect?.let {
                val x = ceil(it.centerX() / epubView.resources.displayMetrics.density).toInt()
                val y = ceil(it.top / epubView.resources.displayMetrics.density).toInt() - 16
                epubView.listener?.onSelection(selection.locator, x, y)
            }
        }

        return true
    }
}

@OptIn(ExperimentalReadiumApi::class)
class EpubFragment : Fragment {
    private var publication: Publication? = null
    private var listener: EpubView? = null
    var navigator: EpubNavigatorFragment? = null

    // Required no-arg constructor for Android Fragment system (used when restoring from saved state)
    constructor() : super(R.layout.fragment_reader)

    constructor(publication: Publication, listener: EpubView) : super(R.layout.fragment_reader) {
        this.publication = publication
        this.listener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val pub = publication
        val lst = listener

        if (pub != null && lst != null) {
            childFragmentManager.fragmentFactory = EpubNavigatorFactory(
                pub,
                EpubNavigatorFactory.Configuration(
                    defaults = EpubDefaults(
                        publisherStyles = false
                    ),
                ),
            ).createFragmentFactory(
                lst.props!!.locator,
                listener = lst,
                configuration = EpubNavigatorFragment.Configuration {
                    servedAssets = BundledFonts.servedAssets
                    shouldApplyInsetsPadding = lst.shouldApplyInsetsPadding

                    // Declared from the same list the picker offers, so a font
                    // can no longer be offered without being served — which is
                    // precisely how the default came to point at a file that did
                    // not exist. Families with no faces need no declaration: the
                    // system or Readium's own assets answer for those.
                    BundledFonts.all.forEach { font ->
                        if (font.faces.isEmpty()) return@forEach
                        addFontFamilyDeclaration(FontFamily(font.family)) {
                            font.faces.forEach { face ->
                                addFontFace {
                                    addSource(face.asset)
                                    setFontStyle(if (face.italic) FontStyle.ITALIC else FontStyle.NORMAL)
                                    when (face) {
                                        is BundledFontFace.Static -> setFontWeight(face.weight)
                                        is BundledFontFace.Variable -> setFontWeight(face.weights)
                                    }
                                }
                            }
                        }
                    }

                    lst.props!!.customFonts.forEach {
                        val fontUrl = checkNotNull(AbsoluteUrl(it.uri)) {
                            "Invalid custom font URI: ${it.uri}"
                        }
                        addFontFamilyDeclaration(FontFamily(it.name)) {
                            addFontFace {
                                addSource(fontUrl)
                                setFontStyle(FontStyle.NORMAL)
                                setFontWeight(FontWeight.NORMAL)
                            }
                        }
                    }

                    selectionActionModeCallback = SelectionActionModeCallback(lst)

                    registerJavascriptInterface("storytellerAPI") {
                        lst.setupUserScript()
                    }
                },
                initialPreferences = EpubPreferences(
                    backgroundColor = if (lst.props!!.publisherStyles || lst.props!!.respectPublisherColors) null
                    else org.readium.r2.navigator.preferences.Color(lst.props!!.background),
                    fontFamily = lst.props!!.fontFamily,
                    fontSize = lst.props!!.fontSize,
                    lineHeight = lst.props!!.lineHeight,
                    paragraphSpacing = lst.props!!.paragraphSpacing,
                    textAlign = lst.props!!.textAlign,
                    textColor = if (lst.props!!.publisherStyles || lst.props!!.respectPublisherColors) null
                    else org.readium.r2.navigator.preferences.Color(lst.props!!.foreground),
                    scroll = lst.props!!.scroll,
                    columnCount = lst.props!!.columnCount,
                    pageMargins = lst.props!!.pageMargins,
                    publisherStyles = lst.props!!.publisherStyles,
                ),
            )
        }

        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // If restored by the system without dependencies, skip setup — EpubView will
        // remove this shell fragment and create a fresh one via initializeNavigator().
        val lst = listener ?: return

        val navigatorFragmentTag = getString(R.string.epub_navigator_tag)

        if (savedInstanceState == null) {
            childFragmentManager.commitNow {
                setReorderingAllowed(true)
                add(
                    R.id.fragment_reader_container,
                    EpubNavigatorFragment::class.java,
                    Bundle(),
                    navigatorFragmentTag
                )
            }
        }
        navigator =
            childFragmentManager.findFragmentByTag(navigatorFragmentTag) as EpubNavigatorFragment
    }
}
