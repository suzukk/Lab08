
package mn.edu.must.sqat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CourseRegistrationServiceTest {

    private StudentRepository repository;
    private EmailSender emailSender;
    private CourseRegistrationService service;

    @BeforeEach
    void setUp() {
        repository = mock(StudentRepository.class);
        emailSender = mock(EmailSender.class);
        service = new CourseRegistrationService(repository, emailSender);
    }

    // A1: Successful registration
    @Test
    void register_successfulRegistration_returnsOK() {
        Student student = new Student(
                "S001",
                "student@example.com",
                true,
                List.of("CS101")
        );

        when(repository.findStudent("S001")).thenReturn(student);
        when(repository.courseExists("CS201")).thenReturn(true);
        when(repository.prerequisites("CS201")).thenReturn(List.of("CS101"));

        String result = service.register("S001", "CS201");

        assertEquals("OK", result);

        verify(repository).saveRegistration("S001", "CS201");
        verify(emailSender).send(
                "student@example.com",
                "Бүртгэл баталгаажлаа",
                "CS201 хичээлд амжилттай бүртгэгдлээ."
        );
    }

    // A2: Student does not exist
    @Test
    void register_missingStudent_returnsError() {
        when(repository.findStudent("S001")).thenReturn(null);

        String result = service.register("S001", "CS201");

        assertEquals("ERROR_NO_STUDENT", result);

        verify(repository, never()).saveRegistration(anyString(), anyString());
        verifyNoInteractions(emailSender);
    }

    // A3: Student is inactive
    @Test
    void register_inactiveStudent_returnsError() {
        Student student = new Student(
                "S001",
                "student@example.com",
                false,
                List.of("CS101")
        );

        when(repository.findStudent("S001")).thenReturn(student);

        String result = service.register("S001", "CS201");

        assertEquals("ERROR_INACTIVE_STUDENT", result);

        verify(repository, never()).saveRegistration(anyString(), anyString());
        verifyNoInteractions(emailSender);
    }

    // A4: Prerequisites have not been completed
    @Test
    void register_missingPrerequisites_returnsError() {
        Student student = new Student(
                "S001",
                "student@example.com",
                true,
                List.of()
        );

        when(repository.findStudent("S001")).thenReturn(student);
        when(repository.courseExists("CS201")).thenReturn(true);
        when(repository.prerequisites("CS201")).thenReturn(List.of("CS101"));

        String result = service.register("S001", "CS201");

        assertEquals("ERROR_PREREQUISITES", result);

        verify(repository, never()).saveRegistration(anyString(), anyString());
        verifyNoInteractions(emailSender);
    }

    // A5: Email failure does not cancel registration
    @Test
    void register_emailFailure_stillReturnsOK() {
        Student student = new Student(
                "S001",
                "student@example.com",
                true,
                List.of("CS101")
        );

        when(repository.findStudent("S001")).thenReturn(student);
        when(repository.courseExists("CS201")).thenReturn(true);
        when(repository.prerequisites("CS201")).thenReturn(List.of("CS101"));

        doThrow(new RuntimeException("Email service unavailable"))
                .when(emailSender)
                .send(anyString(), anyString(), anyString());

        String result = service.register("S001", "CS201");

        assertEquals("OK", result);

        verify(repository).saveRegistration("S001", "CS201");
        verify(emailSender).send(anyString(), anyString(), anyString());
    }

    // A6: Both null inputs are rejected
    @Test
    void register_nullInput_throwsExceptionWithoutInteractions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.register(null, "CS201")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.register("S001", null)
        );

        verifyNoInteractions(repository, emailSender);
    }

    // A7: Capture and inspect the email body
    @Test
    void register_successfulRegistration_sendsCorrectEmailBody() {
        Student student = new Student(
                "S001",
                "student@example.com",
                true,
                List.of("CS101")
        );

        when(repository.findStudent("S001")).thenReturn(student);
        when(repository.courseExists("CS201")).thenReturn(true);
        when(repository.prerequisites("CS201")).thenReturn(List.of("CS101"));

        service.register("S001", "CS201");

        ArgumentCaptor<String> bodyCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(emailSender).send(
                org.mockito.ArgumentMatchers.eq("student@example.com"),
                org.mockito.ArgumentMatchers.eq("Бүртгэл баталгаажлаа"),
                bodyCaptor.capture()
        );

        assertTrue(bodyCaptor.getValue().contains("CS201"));
    }

    // A8: Parameterized tests for different registration errors
    @ParameterizedTest
    @CsvSource({
            "NO_STUDENT, ERROR_NO_STUDENT",
            "INACTIVE_STUDENT, ERROR_INACTIVE_STUDENT",
            "NO_COURSE, ERROR_NO_COURSE"
    })
    @MockitoSettings(strictness = Strictness.LENIENT)
    void register_invalidConditions_returnsExpectedError(
            String scenario,
            String expectedResult
    ) {
        Student activeStudent = new Student(
                "S001",
                "student@example.com",
                true,
                List.of("CS101")
        );

        Student inactiveStudent = new Student(
                "S001",
                "student@example.com",
                false,
                List.of("CS101")
        );

        lenient().when(repository.findStudent("S001"))
                .thenReturn(activeStudent);

        lenient().when(repository.courseExists("CS201"))
                .thenReturn(true);

        if (scenario.equals("NO_STUDENT")) {
            when(repository.findStudent("S001")).thenReturn(null);
        } else if (scenario.equals("INACTIVE_STUDENT")) {
            when(repository.findStudent("S001")).thenReturn(inactiveStudent);
        } else if (scenario.equals("NO_COURSE")) {
            when(repository.courseExists("CS201")).thenReturn(false);
        }

        String result = service.register("S001", "CS201");

        assertEquals(expectedResult, result);

        verify(repository, never()).saveRegistration(anyString(), anyString());
        verifyNoInteractions(emailSender);
    }
}
