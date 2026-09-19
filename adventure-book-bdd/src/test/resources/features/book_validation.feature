Feature: Book validation
  A book can only be played if it is well formed. The rules come straight from the
  assessment spec: exactly one beginning, at least one ending, every option must point
  at a section that exists, and any section that isn't an ending must offer options. A
  fifth rule, not in the original spec but needed to keep section lookup unambiguous,
  requires every section id to be unique within the book.

  Scenario: A well formed book is valid
    Given a book with the following sections:
      | id | type  | options                          |
      | 1  | BEGIN | 2                                 |
      | 2  | NODE  | 3                                 |
      | 3  | END   |                                   |
    When the book is validated
    Then the book should be valid

  Scenario: A book with no beginning is invalid
    Given a book with the following sections:
      | id | type | options |
      | 1  | NODE | 2       |
      | 2  | END  |         |
    When the book is validated
    Then the book should be invalid
    And the validation errors should include "Book has no beginning section"

  Scenario: A book with more than one beginning is invalid
    Given a book with the following sections:
      | id | type  | options |
      | 1  | BEGIN | 3       |
      | 2  | BEGIN | 3       |
      | 3  | END   |         |
    When the book is validated
    Then the book should be invalid
    And the validation errors should include "Book has more than one beginning section (2 found)"

  Scenario: A book with no ending is invalid
    Given a book with the following sections:
      | id | type  | options |
      | 1  | BEGIN | 2       |
      | 2  | NODE  | 1       |
    When the book is validated
    Then the book should be invalid
    And the validation errors should include "Book has no ending section"

  Scenario: A book can have several endings and still be valid
    Given a book with the following sections:
      | id | type  | options |
      | 1  | BEGIN | 2,3     |
      | 2  | END   |         |
      | 3  | END   |         |
    When the book is validated
    Then the book should be valid

  Scenario: An option pointing to a section that doesn't exist is invalid
    Given a book with the following sections:
      | id | type  | options |
      | 1  | BEGIN | 99      |
      | 2  | END   |         |
    When the book is validated
    Then the book should be invalid
    And the validation errors should include "Section 1 has an option pointing to non-existent section 99"

  Scenario: A non-ending section without options is invalid
    Given a book with the following sections:
      | id | type | options |
      | 1  | BEGIN | 2      |
      | 2  | NODE  |        |
    When the book is validated
    Then the book should be invalid
    And the validation errors should include "Section 2 is not an ending but has no options"

  Scenario: A book can fail several rules at once
    Given a book with the following sections:
      | id | type | options |
      | 1  | NODE | 2       |
      | 2  | NODE |         |
    When the book is validated
    Then the book should be invalid
    And the validation errors should include "Book has no beginning section"
    And the validation errors should include "Book has no ending section"
    And the validation errors should include "Section 2 is not an ending but has no options"

  # These scenarios run the application's own validator, but the rule list has to be wired
  # up by hand here because there's no Spring context to discover it. This checks that the
  # hand-written list is still complete, so a sixth rule added to the application can't
  # quietly go unchecked by every scenario above.
  Scenario: Every rule the application ships is exercised by these scenarios
    Then every validation rule the application ships should be covered here

  Scenario: Two sections sharing the same id is invalid
    Given a book with the following sections:
      | id | type  | options |
      | 1  | BEGIN | 7       |
      | 7  | NODE  | 7       |
      | 7  | END   |         |
    When the book is validated
    Then the book should be invalid
    And the validation errors should include "Section number 7 is used by 2 sections"
