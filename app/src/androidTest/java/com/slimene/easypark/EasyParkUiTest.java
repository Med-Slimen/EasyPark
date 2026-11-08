package com.slimene.easypark;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.firebase.auth.FirebaseAuth;
import com.slimene.easypark.repository.SessionRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class EasyParkUiTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void setUp() {
        FirebaseAuth.getInstance().signOut();
        SessionRepository.getInstance().clearSession();
    }

    @Test
    public void testLoginScreenDisplayed() {
        onView(withId(R.id.etEmail)).check(matches(isDisplayed()));
        onView(withId(R.id.etPassword)).check(matches(isDisplayed()));
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
    }

    @Test
    public void testEmptyLoginValidation() {
        onView(withId(R.id.btnLogin)).perform(click());
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
    }

    @Test
    public void testNavigateToRegister() {
        onView(withId(R.id.tvRegisterLink)).perform(click());
        onView(withId(R.id.btnRegister)).check(matches(isDisplayed()));
    }
}
