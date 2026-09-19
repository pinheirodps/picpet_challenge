Feature: Basic game navigation
  Starting a game and moving between sections by picking options, without worrying
  about health consequences yet — this is Objective 2 from the spec.

  Background:
    Given a book with the following sections:
      | id | type  | text                        | options |
      | 1  | BEGIN | You stand at the entrance   | 2,3     |
      | 2  | NODE  | A dark corridor stretches on| 3       |
      | 3  | END   | You made it out             |         |

  Scenario: Starting a game puts the reader on the beginning section
    When a game is started
    Then the current section should be 1
    And the game status should be PLAYING
    And the player's health should be 10

  Scenario: Picking an option moves to the next section
    Given a game is started
    When the player picks option 1
    Then the current section should be 3

  # FINISHED, not WON: an END section can just as easily be a bad ending, so the status
  # says the reader reached one rather than claiming they won.
  Scenario: Reaching an END section finishes the game
    Given a game is started
    When the player picks option 1
    Then the game status should be FINISHED

  Scenario: The reader can stop a game part way through
    Given a game is started
    When the reader stops the game
    Then the game status should be ABANDONED

  Scenario: A stopped game accepts no further choices
    Given a game is started
    And the reader stops the game
    When the player tries to pick option 0 again
    Then the attempt should be rejected

  Scenario: The game keeps going while sections are not endings
    Given a game is started
    When the player picks option 0
    Then the current section should be 2
    And the game status should be PLAYING
