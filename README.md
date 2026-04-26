# OnlyText

OnlyText is a minimal Android text editor built around a four-quadrant workspace.

Instead of treating file browsing and editing as separate modes, OnlyText keeps them side by side in a spatial layout that is fast to navigate, easy to remember, and designed to stay out of the way.

## Core Idea

The app is organized as a 2x2 workspace:

- Four quadrants
- Four editors
- Four matching file managers

Each quadrant is a persistent editing slot. You can think of them as four lightweight text workspaces that stay available at the same time.

## Layout

Each row contains this repeating horizontal structure:

`File Manager | Editor | Editor | File Manager`

This means:

- Quadrant 1 has its own file manager and editor
- Quadrant 2 has its own file manager and editor
- Quadrant 3 has its own file manager and editor
- Quadrant 4 has its own file manager and editor

The file managers are not global. Each one is tied to its corresponding editor slot, so every quadrant can keep its own folder, active file, expanded tree state, and editing context.

## Navigation

OnlyText is built around continuous movement in both directions:

- Infinite horizontal switching
- Infinite vertical switching

Horizontal movement cycles through the file-manager/editor structure.

Vertical movement cycles through the two workspace rows.

The app recenters internally, so navigation feels endless instead of bounded by a fixed set of pages.

## What It Emphasizes

- Four-quadrant workflow for parallel text work
- Four independent editors
- Four corresponding file managers
- Minimal UI
- Plain text editing
- Fast switching instead of window management
- Persistent state
- Auto-save

## Persistence

OnlyText keeps the workspace stable across app restarts.

It preserves:

- Current horizontal and vertical position
- Open folders or files in each quadrant
- Active file in each slot
- Expanded folder state
- File list scroll position
- Editor text
- Cursor selection
- Editor scroll state

This makes the app feel closer to a persistent writing surface than a temporary editor session.

## Auto-Save

Editing is automatically saved with a short debounce.

The app also tracks external file changes and reloads content when needed, so the visible state stays aligned with the underlying file.

## File Access

OnlyText uses Android's document APIs to open files and folders, which allows it to work with user-selected locations through the system picker.

## Tech

- Kotlin
- Jetpack Compose
- Android Storage Access Framework

## License

Licensed under the [AGPL-3.0](LICENSE).
