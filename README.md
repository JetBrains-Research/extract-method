# Extract-Method-Refactoring

*Work in progress. Currently a lot of things either doesn't work the way they should or doesn't work at all*

This is a repository for IntelliJ IDEA plugin that suggests refactoring possibilities based on user-selected region.

## Build and Installation

1. Clone this repository
2. Build plugin using ```./gradlew jar``` 
3. Go to ```Settings-> Plugins-> Install plugin from disk```
4. Locate and select extract-method-refactoring-0.1.jar
5. Restart IntelliJ IDEA

## Usage

This plugin adds new button to the toolbar. *Currently it can be identified by hint: "Partially extract".* After the code region is selected user should click this button and new tool-window with list of refactoring possibilities will show up. By double clicking the appropriate option the chosen refactoring will be applied.