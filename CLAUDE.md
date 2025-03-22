# CraftGPT Development Guidelines

## Build Commands
- Maven project with default goal: `clean package`
- Build command: `mvn clean package`
- No explicit test framework configured
- Plugin can be built with Java 11 (configured in pom.xml)

## Code Style Guidelines
- Java 11 target
- Package structure: `acute.ai.*`
- Class naming: PascalCase (e.g., `AIMob`, `CraftGPT`)
- Method naming: camelCase (e.g., `onEnable()`, `buildPlayerCreatedAIMob()`)
- Field naming: camelCase with no Hungarian notation
- Constants: UPPER_SNAKE_CASE
- Use braces even for single-line blocks
- Prefer descriptive method and variable names
- Mark transient fields appropriately for serialization
- Use ChatColor constants for message formatting

## Project Structure
- Spigot/Bukkit plugin for Minecraft (API version 1.13+)
- Core classes in `acute.ai` package
- Configuration in YAML format (`config.yml`, `plugin.yml`, `usage.yml`)
- Data storage in JSON format (`data.json`)
- Version: 0.2.14
- Main class: `acute.ai.CraftGPT`

## Dependencies
- Spigot API (1.21.1-R0.1-SNAPSHOT)
- OpenAI GPT Java client (0.18.2)
- Jackson for JSON handling (2.14.2)
- Retrofit for HTTP (2.9.0)
- Optional: PlaceholderAPI (2.11.3) 
- Apache Commons Lang (3.7)