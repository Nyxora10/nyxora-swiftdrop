package com.nyxora.swiftdrop.common.security.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiMaskingUtilTest {

    @Nested
    @DisplayName("Email Masking Tests")
    class EmailMaskingTests {

        @Test
        @DisplayName("Should mask standard email address correctly")
        void testStandardEmail() {
            assertThat(PiiMaskingUtil.maskEmail("kamal@gmail.com")).isEqualTo("k***l@gmail.com");
            assertThat(PiiMaskingUtil.maskEmail("john.doe@swiftdrop.lk")).isEqualTo("j***e@swiftdrop.lk");
            assertThat(PiiMaskingUtil.maskEmail("customer@sub.domain.co.uk")).isEqualTo("c***r@sub.domain.co.uk");
        }

        @Test
        @DisplayName("Should mask single-character local part with single asterisk")
        void testSingleCharLocalPart() {
            assertThat(PiiMaskingUtil.maskEmail("a@test.com")).isEqualTo("*@test.com");
            assertThat(PiiMaskingUtil.maskEmail("x@domain.lk")).isEqualTo("*@domain.lk");
        }

        @Test
        @DisplayName("Should mask two-character local part with second char as asterisk")
        void testTwoCharLocalPart() {
            assertThat(PiiMaskingUtil.maskEmail("ab@test.com")).isEqualTo("a*@test.com");
            assertThat(PiiMaskingUtil.maskEmail("me@swiftdrop.lk")).isEqualTo("m*@swiftdrop.lk");
        }

        @Test
        @DisplayName("Should trim whitespace before masking")
        void testEmailWithWhitespace() {
            assertThat(PiiMaskingUtil.maskEmail("  kamal@gmail.com  ")).isEqualTo("k***l@gmail.com");
        }

        @Test
        @DisplayName("Should return null and empty safely without exception")
        void testNullAndEmptyEmail() {
            assertThat(PiiMaskingUtil.maskEmail(null)).isNull();
            assertThat(PiiMaskingUtil.maskEmail("")).isEqualTo("");
            assertThat(PiiMaskingUtil.maskEmail("   ")).isEqualTo("");
        }

        @Test
        @DisplayName("Should return invalid email format as-is")
        void testInvalidEmail() {
            assertThat(PiiMaskingUtil.maskEmail("not-an-email")).isEqualTo("not-an-email");
            assertThat(PiiMaskingUtil.maskEmail("@domain.com")).isEqualTo("@domain.com");
            assertThat(PiiMaskingUtil.maskEmail("user@")).isEqualTo("user@");
        }
    }

    @Nested
    @DisplayName("Personal Name Masking Tests")
    class NameMaskingTests {

        @Test
        @DisplayName("Should mask standard multi-word names preserving first and last characters per token")
        void testStandardNames() {
            assertThat(PiiMaskingUtil.maskName("Kamal Perera")).isEqualTo("K***l P***a");
            assertThat(PiiMaskingUtil.maskName("John Ronald Tolkien")).isEqualTo("J***n R***d T***n");
            assertThat(PiiMaskingUtil.maskName("Alice")).isEqualTo("A***e");
            assertThat(PiiMaskingUtil.maskName("Bob")).isEqualTo("B***b");
        }

        @Test
        @DisplayName("Should mask single-character name tokens with asterisk")
        void testSingleCharNameTokens() {
            assertThat(PiiMaskingUtil.maskName("A")).isEqualTo("*");
            assertThat(PiiMaskingUtil.maskName("A B C")).isEqualTo("* * *");
        }

        @Test
        @DisplayName("Should mask two-character name tokens with first character and asterisk")
        void testTwoCharNameTokens() {
            assertThat(PiiMaskingUtil.maskName("Al")).isEqualTo("A*");
            assertThat(PiiMaskingUtil.maskName("A.")).isEqualTo("A*");
            assertThat(PiiMaskingUtil.maskName("Al Perera")).isEqualTo("A* P***a");
        }

        @Test
        @DisplayName("Should trim leading, trailing, and multiple inter-word whitespace")
        void testNameWhitespaceNormalization() {
            assertThat(PiiMaskingUtil.maskName("  Kamal   Perera  ")).isEqualTo("K***l P***a");
        }

        @Test
        @DisplayName("Should return null and empty safely without exception")
        void testNullAndEmptyName() {
            assertThat(PiiMaskingUtil.maskName(null)).isNull();
            assertThat(PiiMaskingUtil.maskName("")).isEqualTo("");
            assertThat(PiiMaskingUtil.maskName("   ")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("Phone Number Masking Tests")
    class PhoneNumberMaskingTests {

        @Test
        @DisplayName("Should mask Sri Lankan international phone number (+94)")
        void testSriLankaInternationalPhone() {
            assertThat(PiiMaskingUtil.maskPhoneNumber("+94771234567")).isEqualTo("+94*****4567");
            assertThat(PiiMaskingUtil.maskPhoneNumber("+94719876543")).isEqualTo("+94*****6543");
        }

        @Test
        @DisplayName("Should mask domestic phone number starting with 07")
        void testDomesticPhone() {
            assertThat(PiiMaskingUtil.maskPhoneNumber("0771234567")).isEqualTo("07****4567");
            assertThat(PiiMaskingUtil.maskPhoneNumber("0719876543")).isEqualTo("07****6543");
        }

        @Test
        @DisplayName("Should preserve whitespace formatting in phone number while masking digits")
        void testFormattedPhoneWithSpaces() {
            assertThat(PiiMaskingUtil.maskPhoneNumber("+94 77 123 4567")).isEqualTo("+94 ** *** 4567");
        }

        @Test
        @DisplayName("Should preserve hyphen formatting in phone number while masking digits")
        void testFormattedPhoneWithHyphens() {
            assertThat(PiiMaskingUtil.maskPhoneNumber("077-123-4567")).isEqualTo("07*-***-4567");
        }

        @Test
        @DisplayName("Should return short strings as-is if too short to be a valid phone number")
        void testShortPhone() {
            assertThat(PiiMaskingUtil.maskPhoneNumber("12345")).isEqualTo("12345");
            assertThat(PiiMaskingUtil.maskPhoneNumber("911")).isEqualTo("911");
        }

        @Test
        @DisplayName("Should return null and empty safely without exception")
        void testNullAndEmptyPhone() {
            assertThat(PiiMaskingUtil.maskPhoneNumber(null)).isNull();
            assertThat(PiiMaskingUtil.maskPhoneNumber("")).isEqualTo("");
            assertThat(PiiMaskingUtil.maskPhoneNumber("   ")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("Address Masking Tests")
    class AddressMaskingTests {

        @Test
        @DisplayName("Should mask street number with slash (e.g. 123/A)")
        void testAddressWithSlash() {
            assertThat(PiiMaskingUtil.maskAddress("123/A Galle Road, Colombo 03"))
                    .isEqualTo("*** Galle Road, Colombo 03");
            assertThat(PiiMaskingUtil.maskAddress("45/2, Peradeniya Road, Kandy"))
                    .isEqualTo("*** Peradeniya Road, Kandy");
        }

        @Test
        @DisplayName("Should mask standard house number")
        void testStandardHouseNumber() {
            assertThat(PiiMaskingUtil.maskAddress("45 Temple Road, Colombo"))
                    .isEqualTo("*** Temple Road, Colombo");
            assertThat(PiiMaskingUtil.maskAddress("1002 High Level Road, Nugegoda"))
                    .isEqualTo("*** High Level Road, Nugegoda");
        }

        @Test
        @DisplayName("Should mask address with 'No.' prefix")
        void testAddressWithNoPrefix() {
            assertThat(PiiMaskingUtil.maskAddress("No. 45/2, 3rd Lane, Kandy"))
                    .isEqualTo("No. ***, 3rd Lane, Kandy");
            assertThat(PiiMaskingUtil.maskAddress("No 12 Galle Road, Colombo 03"))
                    .isEqualTo("No *** Galle Road, Colombo 03");
        }

        @Test
        @DisplayName("Should leave address without street number unchanged")
        void testAddressWithoutNumber() {
            assertThat(PiiMaskingUtil.maskAddress("Galle Road, Colombo 03"))
                    .isEqualTo("Galle Road, Colombo 03");
            assertThat(PiiMaskingUtil.maskAddress("Central Mail Exchange, Colombo"))
                    .isEqualTo("Central Mail Exchange, Colombo");
        }

        @Test
        @DisplayName("Should return null and empty safely without exception")
        void testNullAndEmptyAddress() {
            assertThat(PiiMaskingUtil.maskAddress(null)).isNull();
            assertThat(PiiMaskingUtil.maskAddress("")).isEqualTo("");
            assertThat(PiiMaskingUtil.maskAddress("   ")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("Free-Text Message Scrubber Tests")
    class TextScrubberTests {

        @Test
        @DisplayName("Should mask both embedded email and phone in arbitrary text")
        void testTextWithEmailAndPhone() {
            String rawLog = "Customer kamal@gmail.com requested parcel delivery from +94771234567";
            String sanitized = PiiMaskingUtil.maskText(rawLog);

            assertThat(sanitized).isEqualTo("Customer k***l@gmail.com requested parcel delivery from +94*****4567");
        }

        @Test
        @DisplayName("Should mask multiple emails and multiple phone numbers")
        void testMultiplePiiOccurrences() {
            String rawLog = "Sender sender@swiftdrop.lk (0771234567) dispatched to receiver@gmail.com (+94719876543)";
            String sanitized = PiiMaskingUtil.maskText(rawLog);

            assertThat(sanitized).contains("s***r@swiftdrop.lk");
            assertThat(sanitized).contains("07****4567");
            assertThat(sanitized).contains("r***r@gmail.com");
            assertThat(sanitized).contains("+94*****6543");
        }

        @Test
        @DisplayName("Should return text as-is if no PII is present")
        void testTextWithoutPii() {
            String rawLog = "Parcel SD-20261102-123456 transitioned to status IN_TRANSIT";
            assertThat(PiiMaskingUtil.maskText(rawLog)).isEqualTo(rawLog);
        }

        @Test
        @DisplayName("Should return null and empty text safely")
        void testNullAndEmptyText() {
            assertThat(PiiMaskingUtil.maskText(null)).isNull();
            assertThat(PiiMaskingUtil.maskText("")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("Utility Class Invariants")
    class UtilityClassInvariantTests {

        @Test
        @DisplayName("Constructor should be private and throw UnsupportedOperationException on reflection")
        void testPrivateConstructor() throws Exception {
            Constructor<PiiMaskingUtil> constructor = PiiMaskingUtil.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            assertThatThrownBy(constructor::newInstance)
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(UnsupportedOperationException.class);
        }
    }
}
