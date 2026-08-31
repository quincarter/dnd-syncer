# GetFocus

A lightweight command-line tool that extracts the currently active macOS Focus mode and writes it to a file.

## Overview

GetFocus reads macOS Focus mode data from the system's Do Not Disturb database and outputs the name of the currently active Focus mode. This is useful for integrating your Focus status into scripts, status bars, or other automation workflows.

## How It Works

The tool reads two JSON files from macOS's Do Not Disturb database:

- `Assertions.json` - Contains active Focus mode assertions (which modes are currently enabled)
- `ModeConfigurations.json` - Contains the human-readable names for each Focus mode

It determines the most recently activated Focus mode and writes its name to an output file.

## Installation

### Prerequisites

- Go 1.25.1 or later
- macOS (this tool is macOS-specific)

### Build from source

```bash
go build -o getfocus main.go
```

## Usage

Basic usage with defaults:

```bash
./getfocus
```

This will create a file `current_focus.txt` containing the active Focus mode name.

### Command-line Options

- `-output` - Specify the output file path (default: `current_focus.txt`)
- `-assertions` - Specify path to Assertions.json (default: `~/Library/DoNotDisturb/DB/Assertions.json`)
- `-modes` - Specify path to ModeConfigurations.json (default: `~/Library/DoNotDisturb/DB/ModeConfigurations.json`)

### Examples

Write to a custom output file:

```bash
./getfocus -output ~/.local/var/current_focus
```

Specify custom database paths:

```bash
./getfocus -assertions /path/to/Assertions.json -modes /path/to/ModeConfigurations.json
```

## Output

The tool writes a single line containing the current Focus mode name to the output file.
If no Focus mode is active, it writes `None`.

Example output:

```plain
Work
```

## Use Cases

- **Status bar integration** - Display your current Focus mode in tmux, i3bar, or other status bars
- **Automation scripts** - Trigger actions based on your current Focus mode
- **Time tracking** - Log when you're in different Focus modes
- **Productivity monitoring** - Track how much time you spend in each Focus mode

## Error Handling

The tool will exit with status code 1 and print an error message to stderr if:

- The Assertions.json or ModeConfigurations.json files cannot be read
- The JSON files cannot be parsed
- The output file cannot be written

## License

MIT License

Copyright (c) 2025

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
