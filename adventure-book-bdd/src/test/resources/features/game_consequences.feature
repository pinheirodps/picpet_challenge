Feature: Health consequences and death
  Some choices cost or restore health. A player starts with 10 health points, and once
  health reaches zero the adventure is over — this is Objective 3 from the spec.

  Background:
    Given a book with the following sections:
      | id | type  | text                    | options                                                                            |
      | 1  | BEGIN | You face a narrow ledge | Jump across\|2\|LOSE_HEALTH\|4\|You scrape your leg on the rock, Turn back\|3      |
      | 2  | NODE  | You made it across      | Rest here\|4\|GAIN_HEALTH\|3\|The fire warms you through                           |
      | 3  | END   | You retreat safely      |                                                                                    |
      | 4  | END   | You reach the summit    |                                                                                    |

  Scenario: A LOSE_HEALTH consequence reduces the player's health
    Given a game is started
    When the player picks option 0
    Then the player's health should be 6
    And the game status should be PLAYING

  Scenario: The reader is told why they lost health
    Given a game is started
    When the player picks option 0
    Then the last consequence should be LOSE_HEALTH of 4
    And the last consequence text should be "You scrape your leg on the rock"

  Scenario: A harmless choice leaves no consequence to explain
    Given a game is started
    When the player picks option 1
    Then there should be no last consequence

  Scenario: A GAIN_HEALTH consequence increases the player's health
    Given a game is started
    And the player picks option 0
    When the player picks option 0
    Then the player's health should be 9

  Scenario: Health never goes above the starting maximum
    Given a game is started
    And the player picks option 0
    When the player picks option 0
    Then the player's health should be at most 10

  Scenario: Reaching zero health kills the player
    Given a book with the following sections:
      | id | type  | text            | options                                    |
      | 1  | BEGIN | A deadly trap   | Walk in\|2\|LOSE_HEALTH\|10                |
      | 2  | END   | The trap closes |                                             |
    And a game is started
    When the player picks option 0
    Then the game status should be DEAD
    And the player's health should be 0

  # The reader is moved before death is decided, so the screen can show them the room they
  # died in rather than the one they walked out of.
  Scenario: A fatal choice still takes the reader where they chose to go
    Given a book with the following sections:
      | id | type  | text            | options                                    |
      | 1  | BEGIN | A deadly trap   | Walk in\|2\|LOSE_HEALTH\|10                |
      | 2  | END   | The trap closes |                                             |
    And a game is started
    When the player picks option 0
    Then the current section should be 2
    And the game status should be DEAD

  Scenario: Death takes precedence over reaching an ending
    Given a book with the following sections:
      | id | type  | text                | options                                       |
      | 1  | BEGIN | The final doorway   | Push through\|2\|LOSE_HEALTH\|10              |
      | 2  | END   | The hall beyond     |                                                |
    And a game is started
    When the player picks option 0
    Then the game status should be DEAD

  Scenario: A dead player cannot make another choice
    Given a book with the following sections:
      | id | type  | text            | options                                    |
      | 1  | BEGIN | A deadly trap   | Walk in\|2\|LOSE_HEALTH\|10                |
      | 2  | END   | The trap closes |                                             |
    And a game is started
    And the player picks option 0
    When the player tries to pick option 0 again
    Then the attempt should be rejected
