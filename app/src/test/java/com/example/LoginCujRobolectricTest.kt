package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.VaultAuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun authIsolationChain(composeRule: org.junit.rules.TestRule): RuleChain =
    RuleChain.outerRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val ctx = ApplicationProvider.getApplicationContext<Context>()
                runBlocking { VaultAuthRepository.getInstance(ctx).resetAccount() }
                try {
                    base.evaluate()
                } finally {
                    runBlocking { VaultAuthRepository.getInstance(ctx).resetAccount() }
                }
            }
        }
    }.around(composeRule)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp")
class LoginCujRobolectricTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = authIsolationChain(composeRule)

    @Test
    fun mobileLoginGate_protectsStudioAndSupportsRegisterWrongPasswordLockAndUnlock() {
        // Wait for auth snapshot to finish loading from DataStore
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("login_number_input").fetchSemanticsNodes().isNotEmpty()
        }

        // Studio drop zone must NOT be accessible while locked
        assert(composeRule.onAllNodesWithTag("drag_drop_zone").fetchSemanticsNodes().isEmpty())

        // Test 1: Mismatched confirm password shows validation error
        composeRule.onNodeWithTag("login_number_input").performScrollTo().performTextInput("01712345678")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("VaultPass#99")
        composeRule.onNodeWithTag("login_confirm_password_input").performScrollTo().performTextInput("Mismatch#00")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("login_error_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("login_error_text").performScrollTo().assertIsDisplayed()

        // Test 2: Valid registration unlocks Studio
        composeRule.onNodeWithTag("login_confirm_password_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("login_confirm_password_input").performScrollTo().performTextInput("VaultPass#99")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("drag_drop_zone").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("drag_drop_zone").assertIsDisplayed()

        // Test 3: Lock app from Vault tab
        composeRule.onNodeWithTag("nav_vault").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("lock_vault_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("lock_vault_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("login_number_input").fetchSemanticsNodes().isNotEmpty()
        }

        // Test 4: Wrong password blocks access and shows error
        composeRule.onNodeWithTag("login_number_input").performScrollTo().performTextInput("01712345678")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("WrongPass123")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("login_error_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("login_error_text").performScrollTo().assertIsDisplayed()
        assert(composeRule.onAllNodesWithTag("drag_drop_zone").fetchSemanticsNodes().isEmpty())

        // Test 5: Correct number and password unlocks Studio cleanly
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("VaultPass#99")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("drag_drop_zone").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("drag_drop_zone").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class DesktopLoginCujRobolectricTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = authIsolationChain(composeRule)

    @Test
    fun pcDesktopLayout_usesNavigationRailAndTwoPaneStudioAfterLogin() {
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("login_number_input").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("login_number_input").performTextInput("987654")
        composeRule.onNodeWithTag("login_password_input").performTextInput("DesktopPass1")
        composeRule.onNodeWithTag("login_confirm_password_input").performTextInput("DesktopPass1")
        composeRule.onNodeWithTag("login_submit_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("rail_studio").fetchSemanticsNodes().isNotEmpty()
        }
        // PC wide layout shows NavigationRail and Studio drop zone side-by-side with language selector
        composeRule.onNodeWithTag("rail_studio").assertIsDisplayed()
        composeRule.onNodeWithTag("drag_drop_zone").assertIsDisplayed()
        composeRule.onNodeWithTag("lang_chip_bn-bd").assertIsDisplayed()
        composeRule.onNodeWithTag("transcribe_now_button").assertIsDisplayed()

        // Navigate via NavigationRail to Vault and verify Reset Vault flow
        composeRule.onNodeWithTag("rail_vault").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("lock_vault_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("lock_vault_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("reset_vault_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reset_vault_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_reset_vault_button").performClick()

        // Confirm reset returns to first-time registration screen (confirm password field reappears)
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("login_confirm_password_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("login_confirm_password_input").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1024dp-h580dp")
class VaultCredentialsCujRobolectricTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = authIsolationChain(composeRule)

    @Test
    fun changeNumberAndPassword_replacesOldCredentialsAndRejectsOldPassword() {
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("login_number_input").fetchSemanticsNodes().isNotEmpty()
        }

        // Register initial number + password on compact-height PC window
        composeRule.onNodeWithTag("login_number_input").performScrollTo().performTextInput("445566")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("FirstPass1")
        composeRule.onNodeWithTag("login_confirm_password_input").performScrollTo().performTextInput("FirstPass1")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("rail_vault").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Vault and toggle Auto-Lock + Change Number/Password
        composeRule.onNodeWithTag("rail_vault").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("change_credentials_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auto_lock_switch").performClick()
        composeRule.onNodeWithTag("change_credentials_button").performClick()

        // Try wrong current password first
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("change_current_password_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("change_current_password_input").performTextInput("WrongCurrent")
        composeRule.onNodeWithTag("change_new_number_input").performTextInput("778899")
        composeRule.onNodeWithTag("change_new_password_input").performTextInput("SecondPass2")
        composeRule.onNodeWithTag("change_confirm_password_input").performTextInput("SecondPass2")
        composeRule.onNodeWithTag("save_credentials_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("change_credentials_error_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("change_credentials_error_text").assertIsDisplayed()

        // Enter correct current password and save new credentials
        composeRule.onNodeWithTag("change_current_password_input").performTextClearance()
        composeRule.onNodeWithTag("change_current_password_input").performTextInput("FirstPass1")
        composeRule.onNodeWithTag("save_credentials_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("save_credentials_button").fetchSemanticsNodes().isEmpty()
        }

        // Lock and verify old credentials fail while new credentials succeed
        composeRule.onNodeWithTag("lock_vault_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("login_number_input").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("login_number_input").performScrollTo().performTextInput("445566")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("FirstPass1")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("login_error_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("login_error_text").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("login_number_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("login_number_input").performScrollTo().performTextInput("778899")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("SecondPass2")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithTag("rail_studio").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("rail_studio").assertIsDisplayed()
    }
}
