package com.nyxora.swiftdrop.parcel.statemachine;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Guarded Parcel Lifecycle State Machine Unit Tests (Member 6)")
class ParcelStateMachineTest {

    private ParcelStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new ParcelStateMachine();
    }

    @Nested
    @DisplayName("1. Topological Transition Matrix Tests")
    class TopologicalTransitionMatrixTests {

        @Test
        @DisplayName("CREATED can only transition to ASSIGNED")
        void createdOnlyTransitionsToAssigned() {
            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.CREATED, ParcelStatus.ASSIGNED)).isTrue();
            assertThat(stateMachine.getAllowedNextStatuses(ParcelStatus.CREATED)).containsExactly(ParcelStatus.ASSIGNED);

            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.CREATED, ParcelStatus.PICKED_UP)).isFalse();
            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.CREATED, ParcelStatus.DELIVERED)).isFalse();
        }

        @Test
        @DisplayName("ASSIGNED can only transition to PICKED_UP")
        void assignedOnlyTransitionsToPickedUp() {
            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.ASSIGNED, ParcelStatus.PICKED_UP)).isTrue();
            assertThat(stateMachine.getAllowedNextStatuses(ParcelStatus.ASSIGNED)).containsExactly(ParcelStatus.PICKED_UP);

            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.ASSIGNED, ParcelStatus.IN_TRANSIT)).isFalse();
        }

        @Test
        @DisplayName("PICKED_UP can only transition to IN_TRANSIT")
        void pickedUpOnlyTransitionsToInTransit() {
            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT)).isTrue();
            assertThat(stateMachine.getAllowedNextStatuses(ParcelStatus.PICKED_UP)).containsExactly(ParcelStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("IN_TRANSIT can only transition to OUT_FOR_DELIVERY")
        void inTransitOnlyTransitionsToOutForDelivery() {
            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY)).isTrue();
            assertThat(stateMachine.getAllowedNextStatuses(ParcelStatus.IN_TRANSIT)).containsExactly(ParcelStatus.OUT_FOR_DELIVERY);
        }

        @Test
        @DisplayName("OUT_FOR_DELIVERY can transition to DELIVERED or FAILED")
        void outForDeliveryTransitions() {
            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED)).isTrue();
            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED)).isTrue();
            assertThat(stateMachine.getAllowedNextStatuses(ParcelStatus.OUT_FOR_DELIVERY))
                    .containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.FAILED);
        }

        @Test
        @DisplayName("FAILED can transition to OUT_FOR_DELIVERY (retry) or RETURNED_TO_SENDER")
        void failedTransitions() {
            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.FAILED, ParcelStatus.OUT_FOR_DELIVERY)).isTrue();
            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.FAILED, ParcelStatus.RETURNED_TO_SENDER)).isTrue();
            assertThat(stateMachine.getAllowedNextStatuses(ParcelStatus.FAILED))
                    .containsExactlyInAnyOrder(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.RETURNED_TO_SENDER);
        }

        @Test
        @DisplayName("Terminal states DELIVERED and RETURNED_TO_SENDER allow no further transitions")
        void terminalStatesAllowNoTransitions() {
            assertThat(stateMachine.getAllowedNextStatuses(ParcelStatus.DELIVERED)).isEmpty();
            assertThat(stateMachine.getAllowedNextStatuses(ParcelStatus.RETURNED_TO_SENDER)).isEmpty();

            for (ParcelStatus target : ParcelStatus.values()) {
                assertThat(stateMachine.isTransitionAllowed(ParcelStatus.DELIVERED, target)).isFalse();
                assertThat(stateMachine.isTransitionAllowed(ParcelStatus.RETURNED_TO_SENDER, target)).isFalse();
            }
        }

        @Test
        @DisplayName("Null status checks safely return false and empty sets")
        void nullStatusHandling() {
            assertThat(stateMachine.isTransitionAllowed(null, ParcelStatus.CREATED)).isFalse();
            assertThat(stateMachine.isTransitionAllowed(ParcelStatus.CREATED, null)).isFalse();
            assertThat(stateMachine.isTransitionAllowed(null, null)).isFalse();
            assertThat(stateMachine.getAllowedNextStatuses(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("2. State Transition Validation Guard Tests")
    class ValidationGuardTests {

        @Test
        @DisplayName("Null current or target status throws InvalidStateTransitionException")
        void nullStatusThrowsException() {
            assertThatThrownBy(() -> stateMachine.validateTransition(null, ParcelStatus.ASSIGNED, null, null, null, 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Current status and target status must not be null.");

            assertThatThrownBy(() -> stateMachine.validateTransition(ParcelStatus.CREATED, null, null, null, null, 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Current status and target status must not be null.");
        }

        @Test
        @DisplayName("Modifying a terminal state throws InvalidStateTransitionException")
        void terminalStateModificationThrowsException() {
            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.DELIVERED, ParcelStatus.OUT_FOR_DELIVERY, null, null, null, 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("terminal state 'DELIVERED'");

            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.RETURNED_TO_SENDER, ParcelStatus.ASSIGNED, null, null, null, 3))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("terminal state 'RETURNED_TO_SENDER'");
        }

        @Test
        @DisplayName("Transitioning to the same status throws InvalidStateTransitionException")
        void sameStatusThrowsException() {
            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.ASSIGNED, ParcelStatus.ASSIGNED, null, null, null, 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("already in status 'ASSIGNED'");
        }

        @Test
        @DisplayName("Illegal topological leap throws InvalidStateTransitionException")
        void illegalTransitionThrowsException() {
            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.CREATED, ParcelStatus.DELIVERED, "John Doe", "https://s3/proof.jpg", null, 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Illegal state transition from 'CREATED' to 'DELIVERED'");
        }

        @Test
        @DisplayName("DELIVERED without recipient signature throws InvalidStateTransitionException")
        void deliveredWithoutSignatureThrowsException() {
            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, null, "https://s3/proof.jpg", null, 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Recipient signature name is strictly required");

            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, "   ", "https://s3/proof.jpg", null, 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Recipient signature name is strictly required");
        }

        @Test
        @DisplayName("DELIVERED without proof photo URL throws InvalidStateTransitionException")
        void deliveredWithoutProofUrlThrowsException() {
            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, "John Doe", null, null, 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Proof photo or signature URL is strictly required");

            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, "John Doe", "   ", null, 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Proof photo or signature URL is strictly required");
        }

        @Test
        @DisplayName("DELIVERED with signature and proof photo validates successfully")
        void deliveredWithProofSuccess() {
            assertThatCode(() -> stateMachine.validateTransition(
                    ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED,
                    "Kamal Perera", "https://swiftdrop-delivery-proofs.s3.amazonaws.com/proof1.png", null, 0))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("FAILED without reason throws InvalidStateTransitionException")
        void failedWithoutReasonThrowsException() {
            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, null, null, null, 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Failure reason is strictly required");

            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, null, null, "   ", 0))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Failure reason is strictly required");
        }

        @Test
        @DisplayName("FAILED with reason validates successfully")
        void failedWithReasonSuccess() {
            assertThatCode(() -> stateMachine.validateTransition(
                    ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, null, null, "RECIPIENT_UNAVAILABLE", 0))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Re-attempt allowed if failure count is less than MAX_FAILED_ATTEMPTS (3)")
        void reattemptAllowedWhenUnderLimit() {
            assertThatCode(() -> stateMachine.validateTransition(
                    ParcelStatus.FAILED, ParcelStatus.OUT_FOR_DELIVERY, null, null, null, 1))
                    .doesNotThrowAnyException();

            assertThatCode(() -> stateMachine.validateTransition(
                    ParcelStatus.FAILED, ParcelStatus.OUT_FOR_DELIVERY, null, null, null, 2))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Re-attempt rejected when failure count >= MAX_FAILED_ATTEMPTS (3)")
        void reattemptRejectedWhenLimitReached() {
            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.FAILED, ParcelStatus.OUT_FOR_DELIVERY, null, null, null, 3))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Maximum failed delivery attempts (3) reached");

            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.FAILED, ParcelStatus.OUT_FOR_DELIVERY, null, null, null, 4))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Maximum failed delivery attempts (3) reached");
        }

        @Test
        @DisplayName("Transition to RETURNED_TO_SENDER rejected when failure count < 3")
        void returnToSenderRejectedUnderLimit() {
            assertThatThrownBy(() -> stateMachine.validateTransition(
                    ParcelStatus.FAILED, ParcelStatus.RETURNED_TO_SENDER, null, null, null, 1))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Cannot transition to RETURNED_TO_SENDER before 3 failed attempts");
        }

        @Test
        @DisplayName("Transition to RETURNED_TO_SENDER succeeds when failure count >= 3")
        void returnToSenderAllowedAtLimit() {
            assertThatCode(() -> stateMachine.validateTransition(
                    ParcelStatus.FAILED, ParcelStatus.RETURNED_TO_SENDER, null, null, null, 3))
                    .doesNotThrowAnyException();

            assertThatCode(() -> stateMachine.validateTransition(
                    ParcelStatus.FAILED, ParcelStatus.RETURNED_TO_SENDER, null, null, null, 4))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Valid standard happy path transitions succeed")
        void happyPathTransitionsSucceed() {
            assertThatCode(() -> stateMachine.validateTransition(ParcelStatus.CREATED, ParcelStatus.ASSIGNED, null, null, null, 0)).doesNotThrowAnyException();
            assertThatCode(() -> stateMachine.validateTransition(ParcelStatus.ASSIGNED, ParcelStatus.PICKED_UP, null, null, null, 0)).doesNotThrowAnyException();
            assertThatCode(() -> stateMachine.validateTransition(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, null, null, null, 0)).doesNotThrowAnyException();
            assertThatCode(() -> stateMachine.validateTransition(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY, null, null, null, 0)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("3. Exception and Enum Coverage Tests")
    class ExceptionAndEnumCoverageTests {

        @Test
        @DisplayName("InvalidStateTransitionException constructors and getters")
        void exceptionConstructorsAndGetters() {
            InvalidStateTransitionException ex1 = new InvalidStateTransitionException(
                    ParcelStatus.CREATED, ParcelStatus.DELIVERED, "Direct delivery not allowed");
            assertThat(ex1.getFromStatus()).isEqualTo(ParcelStatus.CREATED);
            assertThat(ex1.getToStatus()).isEqualTo(ParcelStatus.DELIVERED);
            assertThat(ex1.getMessage()).isEqualTo("Direct delivery not allowed");

            InvalidStateTransitionException ex2 = new InvalidStateTransitionException("Generic error");
            assertThat(ex2.getFromStatus()).isNull();
            assertThat(ex2.getToStatus()).isNull();
            assertThat(ex2.getMessage()).isEqualTo("Generic error");
        }

        @ParameterizedTest
        @EnumSource(ParcelStatus.class)
        @DisplayName("ParcelStatus enum values and terminal state verification")
        void parcelStatusEnumValues(ParcelStatus status) {
            if (status == ParcelStatus.DELIVERED || status == ParcelStatus.RETURNED_TO_SENDER) {
                assertThat(status.isTerminal()).isTrue();
            } else {
                assertThat(status.isTerminal()).isFalse();
            }
        }
    }
}

