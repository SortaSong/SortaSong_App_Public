# Contributing to SortaSong

Thank you for your interest in contributing to SortaSong! This document provides guidelines and instructions for contributing to the project.

## How to Contribute

### Reporting Issues

If you find a bug or have a feature request:

1. Check if the issue already exists in the GitHub issue tracker
2. If not, create a new issue with:
   - A clear, descriptive title
   - Detailed description of the issue or feature
   - Steps to reproduce (for bugs)
   - Expected vs. actual behavior
   - Screenshots if applicable
   - Device/Android version information

### Submitting Changes

1. **Fork the Repository**
   - Fork the project to your GitHub account
   - Clone your fork locally

2. **Create a Branch**
   - Use descriptive branch names following this convention:
     - `feature/description` for new features
     - `bugfix/description` for bug fixes
     - `hotfix/description` for urgent fixes
     - `refactor/description` for code refactoring
     - `docs/description` for documentation changes

3. **Make Your Changes**
   - Follow the code style guidelines (see below)
   - Write clear, concise commit messages
   - Test your changes thoroughly
   - Update documentation if needed

4. **Submit a Pull Request**
   - Push your changes to your fork
   - Open a pull request against the `main` branch
   - Provide a clear description of the changes
   - Reference any related issues
   - Wait for review and address any feedback

## Development Setup

### Prerequisites

- Android Studio (Arctic Fox or newer)
- JDK 17 or higher
- Android SDK (API 24+)

### Setting Up

1. Clone the repository:
```bash
git clone <your-fork-url>
cd SortaSong_App_Public
```

2. Create `local.properties` with your Android SDK path:
```properties
sdk.dir=/path/to/your/android/sdk
```

3. Open in Android Studio and sync Gradle

4. Build the project:
```bash
./gradlew assembleDebug
```

## Code Style Guidelines

### Kotlin Conventions

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Prefer immutability (`val` over `var`)
- Use Kotlin standard library functions when appropriate
- Document public APIs with KDoc comments

### Android Best Practices

- Follow Android architecture best practices
- Use repository pattern for data access
- Implement proper lifecycle handling
- Use view binding instead of `findViewById`
- Handle configuration changes appropriately
- Follow Material Design guidelines

### Code Organization

- Keep files focused on a single responsibility
- Use proper package structure
- Separate business logic from UI logic
- Use data classes for models
- Implement proper error handling

## Testing

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run tests for a specific module
./gradlew app:test
```

### Writing Tests

- Write unit tests for business logic
- Write instrumented tests for UI and integration
- Aim for meaningful test coverage
- Use descriptive test names
- Follow the Arrange-Act-Assert pattern

## Pull Request Guidelines

### Before Submitting

- [ ] Code follows the style guidelines
- [ ] All tests pass locally
- [ ] No new warnings or errors
- [ ] Documentation is updated if needed
- [ ] Commit messages are clear and descriptive

### PR Description Should Include

- Summary of changes
- Motivation and context
- Related issue numbers (e.g., "Fixes #123")
- Screenshots/videos for UI changes
- Testing performed
- Any breaking changes

## Language Note

The current codebase uses German for:
- UI strings (strings.xml)
- Code comments
- Activity/class names (some)

When contributing:
- Keep UI strings in German for consistency
- Comments in English are acceptable
- New code can use English naming conventions

## Code Review Process

1. Maintainers will review your PR
2. Address any requested changes
3. Once approved, your PR will be merged
4. Your contribution will be credited

## Questions?

If you have questions about contributing, feel free to:
- Open a GitHub issue with the "question" label
- Reach out to the maintainers

## License

By contributing, you agree that your contributions will be licensed under the same CC BY-NC-SA 4.0 license as the project.

Thank you for contributing to SortaSong! 🎵
