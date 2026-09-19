Feature: Saving and resuming progress
  A game is saved automatically on every choice — there's no separate save action. The
  player can see every game still in progress and pick one up again where they left off.
  This is Objective 4 (extra) from the spec.

  Background:
    Given a book with the following sections:
      | id | type  | text                       | options |
      | 1  | BEGIN | You stand at the entrance  | 2,3     |
      | 2  | NODE  | A dark corridor stretches  | 3       |
      | 3  | END   | You made it out            |         |

  Scenario: Starting a game saves it automatically
    When a game is started and saved
    Then the saved games list should contain 1 game

  Scenario: An in-progress game appears in the saved games list
    Given a game is started and saved
    When the player picks option 0 and the game is saved
    Then the saved games list should contain 1 game
    And the current section should be 2

  Scenario: A finished game no longer appears in the saved games list
    Given a game is started and saved
    When the player picks option 1 and the game is saved
    Then the game status should be FINISHED
    And the saved games list should contain 0 games

  Scenario: A stopped game no longer appears in the saved games list
    Given a game is started and saved
    When the reader stops the game and it is saved
    Then the game status should be ABANDONED
    And the saved games list should contain 0 games

  Scenario: A player can resume a saved game from where they left off
    Given a game is started and saved
    And the player picks option 0 and the game is saved
    When the saved game is resumed
    Then the current section should be 2
    And the player's health should be 10

  Scenario: Finishing one game while another stays in progress only saves the unfinished one
    Given a game is started and saved
    And the player picks option 1 and the game is saved
    And a second game is started and saved
    Then the saved games list should contain 1 game
