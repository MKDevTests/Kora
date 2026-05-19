package snd.komelia.db.migrations

import io.github.snd_r.komelia.db.sqlite.sqlite.generated.resources.Res
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class AppMigrations : MigrationResourcesProvider() {

    private val migrations = listOf(
        "V1__initial_migration.sql",
        "V2__komga_webui_reader_settings.sql",
        "V3__exposed_migration.sql",
        "V4__settings_reorganisation.sql",
        "V5__color_correction.sql",
        "V6__eink_screen_flash.sql",
        "V7__reader_sampling_settings.sql",
        "V8__thumbnail_previews.sql",
        "V9__volume_keys_navigation.sql",
        "V10__komf_settings.sql",
        "V11__home_filters.sql",
        "V12__offline_mode.sql",
        "V13__ui_colors.sql",
        "V14__immersive_layout.sql",
        "V15__new_library_ui.sql",
        "V16__panel_reader_settings.sql",
        "V17__reader_tap_settings.sql",
        "V18__reader_adaptive_background.sql",
        "V19__card_layout_below.sql",
        "V20__reader_tap_navigation_mode.sql",
        "V21__ncnn_upscaler_settings.sql",
        "V22__ncnn_upscale_on_load.sql",
        "V23__last_selected_library.sql",
        "V24__immersive_color_settings.sql",
        "V25__model_management_settings.sql",
        "V26__hide_parentheses_in_names.sql",
        "V27__keep_reader_screen_on.sql",
        "V28__card_layout_overlay_background.sql",
        "V29__epub3_native_settings.sql",
        "V30__show_immersive_nav_bar.sql",
        "V31__new_library_ui_2.sql",
        "V32__epub3_margins.sql",
        "V33__use_immersive_morphing_cover.sql",
        "V34__lock_screen_rotation.sql",
        "V35__epub3_overlay_settings.sql",
        "V36__epub3_bookmarks.sql",
        "V37__thumbnail_presentation_settings.sql",
        "V38__card_shadow_and_corner_radius.sql",
        "V39__local_file_read_progress.sql",
        "V40__audio_folder.sql",
        "V41__use_floating_navigation_bar.sql",
        "V42__epub3_respect_publisher_colors.sql",
        "V43__book_annotations.sql",
        "V44__annotation_updated_at.sql",
        "V45__audio_chapter_cache.sql",
        "V46__cache_management_settings.sql",
        "V47__transcription_settings.sql",
        "V48__ocr_settings.sql",
        "V49__ocr_merge_boxes.sql",
        "V50__rapid_ocr_models.sql",
        "V51__update_rapid_ocr_url.sql",
        "V52__library_series_filters.sql",
        "V53__split_double_pages.sql",
        "V54__paged_reader_auto_direction.sql",
        "V55__series_reader_overrides.sql",
        "V56__paged_auto_skip_blank_pages.sql",
        "V57__paged_auto_detect_webtoon.sql",
        "V58__continuous_reader_stop_at_end.sql",
        // V59 was burned by an abandoned feat/webtoon-snap-panels branch that
        // some users installed for testing. Their Flyway schema_history has
        // V59 with a different checksum, so reusing it here would crash on
        // startup with FlywayValidateException. Skip V59 forever.
        "V60__search_fuzzy_enabled.sql",
        "V61__navigation_settings.sql",
        "V62__reading_stats.sql",
    )

    override suspend fun getMigration(name: String): ByteArray? {
        return try {
            Res.readBytes("files/migrations/app/$name")
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    override suspend fun getMigrations(): Map<String, ByteArray> {
        return migrations.associateWith { Res.readBytes("files/migrations/app/$it") }
    }
}