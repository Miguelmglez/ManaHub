# Separate Google Sign-In/Sign-Up and Fix Nickname/Avatar Defaults

This plan separates the Google authentication logic in the `AuthViewModel` and `AuthRepository` to distinguish between "Sign In" and "Sign Up". It also ensures that Google's default profile picture and name/email prefix are not used as the user's avatar and nickname, preferring the values provided by the user during sign-up.

## Proposed Changes

### Auth Domain Layer

#### [AuthRepository.kt](file:///C:/Users/Miguel/AndroidStudioProjects/ManaHub/app/src/main/java/com/mmg/manahub/feature/auth/domain/repository/AuthRepository.kt)
- Split `signInWithGoogleIdToken` into `signInWithGoogle` and `signUpWithGoogle`.

#### [SignInWithGoogleUseCase.kt](file:///C:/Users/Miguel/AndroidStudioProjects/ManaHub/app/src/main/java/com/mmg/manahub/feature/auth/domain/usecase/SignInWithGoogleUseCase.kt)
- Update signature to remove `nickname` and `avatarUrl` parameters.

#### [NEW] [SignUpWithGoogleUseCase.kt](file:///C:/Users/Miguel/AndroidStudioProjects/ManaHub/app/src/main/java/com/mmg/manahub/feature/auth/domain/usecase/SignUpWithGoogleUseCase.kt)
- Create a new use case for signing up with Google, taking `idToken`, `rawNonce`, `nickname`, and `avatarUrl`.

---

### Auth Data Layer

#### [AuthRepositoryImpl.kt](file:///C:/Users/Miguel/AndroidStudioProjects/ManaHub/app/src/main/java/com/mmg/manahub/feature/auth/data/repository/AuthRepositoryImpl.kt)
- Update `UserInfo.toAuthUser()` mapper to:
    - Ignore `avatar_url` from metadata if the provider is Google.
    - Avoid email prefix fallback for nickname if the provider is Google.
- Implement `signInWithGoogle`:
    - Perform authentication.
    - Fetch profile; if it doesn't exist, create one with defaults (now nulls for Google).
- Implement `signUpWithGoogle`:
    - Perform authentication.
    - Always upsert the user profile with the provided `nickname` and `avatarUrl` to ensure they are used over any defaults.

---

### Auth Presentation Layer

#### [AuthViewModel.kt](file:///C:/Users/Miguel/AndroidStudioProjects/ManaHub/app/src/main/java/com/mmg/manahub/feature/auth/presentation/AuthViewModel.kt)
- Inject `SignUpWithGoogleUseCase`.
- Update `signInWithGoogle(activityContext: Context)` to handle login only.
- Add `signUpWithGoogle(activityContext: Context, nickname: String, avatarUrl: String?)` to handle sign-up.

#### [LoginSheet.kt](file:///C:/Users/Miguel/AndroidStudioProjects/ManaHub/app/src/main/java/com/mmg/manahub/feature/auth/presentation/LoginSheet.kt)
- Update `LoginSheetContent` to separate `onGoogleSignIn` and `onGoogleSignUp`.
- Call the appropriate method from the Google button `onClick` based on the currently selected tab (`selectedTab == 1` for Sign Up).

## Verification Plan

### Automated Tests
- Run existing tests in `AuthRepositoryImplTest.kt` to ensure no regressions in email/password auth.
- I will create a new test file or add tests to `AuthRepositoryImplTest.kt` to verify the new Google sign-in/sign-up logic.
- Command: `./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.auth.*"`

### Manual Verification
- Deploy the app and test Google Sign-In from the "Sign In" tab:
    - Verify it logs in existing users without changing their profile.
- Test Google Sign-In from the "Sign Up" tab:
    - Enter a custom nickname and select an avatar.
    - Verify the resulting profile uses the entered nickname and avatar, NOT the Google defaults.
- Verify that a NEW Google user signing in for the first time (not sign up) doesn't get a default avatar or an email-based nickname.
