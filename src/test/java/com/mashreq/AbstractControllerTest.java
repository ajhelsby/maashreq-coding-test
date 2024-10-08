package com.mashreq;

import com.jayway.jsonpath.JsonPath;
import com.mashreq.security.AuthenticatedUser;
import com.mashreq.users.UserRepository;
import com.mashreq.users.User;
import jakarta.persistence.EntityManagerFactory;
import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.StringUtils;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * We are running all unit tests in parallel.
 *
 * <p>Any class using @MockBean in their tests needs to be annotated with @Isolated to avoid of
 * resetting of the mock while running tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(CleanDatabaseExtension.class)
// default to JPA and local link builder
@TestPropertySource(properties = {"modules.auth=JPA"})
public abstract class AbstractControllerTest {

  protected static final String LINK_URL = "https://localhost:8080";
  protected static final String SIGNUP_URL = "/api/v1/auth/signup";
  protected static final String LOGIN_URL = "/api/v1/auth/login";
  protected static final String ROOMS_URL = "/api/v1/rooms";
  protected static final String BOOKINGS_URL = "/api/v1/bookings";
  protected static final String USERS_INIT_URL = "/api/v1/users/init";
  public static final String ACCOUNT_PASSWORD = "Un1tT3st";
  protected String userEmail;
  protected String adminEmail;
  @Autowired
  protected MockMvc mockMvc;
  @Autowired
  protected UserRepository userRepository;
  @Autowired
  protected PasswordEncoder passwordEncoder;
  @Autowired
  private EntityManagerFactory emf;

  /**
   * Helper if you need to clean Database manually e.g. in Isolated test
   */
  public void clearDB() {
    var em = emf.createEntityManager();
    var transaction = em.getTransaction();
    transaction.begin();

    // need to truncate all at the same time due to foreign key constraint
    // or user DELETE statements per entity
    em.createNativeQuery("DELETE FROM bookings").executeUpdate();
    em.createNativeQuery("DELETE FROM users").executeUpdate();

    transaction.commit();
    em.close();
  }

  private static int modelSequenceNumber = 0;

  protected static int getModelSequenceNumber() {
    return modelSequenceNumber++;
  }

  protected static String generateEmail() {
    return UUID.randomUUID() + "@mashreq.com";
  }

  protected static User buildUser() {
    return buildUser(generateEmail(), "Test " + getModelSequenceNumber(), "User", null);
  }

  public static User buildUser(
      String email,
      String firstName,
      String lastName,
      String password) {
    return User.builder()
               .email(email)
               .firstName(StringUtils.hasText(firstName) ? firstName : "User " + getModelSequenceNumber())
               .lastName(StringUtils.hasText(firstName) ? lastName : "User")
               .password(password)
               .build();
  }

  protected User givenUser(){
    User user = buildUser();
    return givenUser(user.getEmail(), user.getFirstName(), user.getLastName(), user.getPassword());
  }

  protected User givenUser(
      String email,
      String firstName,
      String lastName,
      String password) {
    return userRepository.save(
        User.builder()
            .email(email)
            .firstName(StringUtils.hasText(firstName) ? firstName : "User")
            .lastName(StringUtils.hasText(lastName) ? lastName : "User")
            .password(
                passwordEncoder.encode(StringUtils.hasText(password) ? password : ACCOUNT_PASSWORD))
            .build());
  }

  public AuthenticatedUser givenAuthUser() {
    return givenAuthUser(UUID.randomUUID(), generateEmail());
  }

  public AuthenticatedUser givenAuthUser(User user) {
    return new AuthenticatedUser(user);
  }

  public AuthenticatedUser givenAuthUser(
      UUID userId,
      String email) {

    // don't need to persist auth user, as we are not hitting UserDetails service to check
    var user = User.builder()
                   .email(email)
                   .firstName("Auth")
                   .lastName("User")
                   .password(passwordEncoder.encode(ACCOUNT_PASSWORD))
                   .build();
    user.setId(userId);

    return new AuthenticatedUser(user);
  }

  protected String getJsonValue(ResultActions result, String jsonPath)
      throws UnsupportedEncodingException {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
  }

  protected ResultActions callGet(String authToken, String url) throws Exception {
    return buildAndExecRequest("GET", authToken, null, url, null);
  }

  protected ResultActions callGet(AuthenticatedUser authUser, String url) throws Exception {
    return buildAndExecRequest("GET", null, authUser, url, null);
  }

  protected ResultActions callPost(String url, String payload) throws Exception {
    return buildAndExecRequest("POST", null, null, url, payload);
  }

  protected ResultActions callPost(String authToken, String url, String payload) throws Exception {
    return buildAndExecRequest("POST", authToken, null, url, payload);
  }

  protected ResultActions callPost(AuthenticatedUser authUser, String url, String payload)
      throws Exception {
    return buildAndExecRequest("POST", null, authUser, url, payload);
  }

  protected ResultActions callPut(String url, String payload) throws Exception {
    return buildAndExecRequest("PUT", null, null, url, payload);
  }

  protected ResultActions callPut(String authToken, String url, String payload) throws Exception {
    return buildAndExecRequest("PUT", authToken, null, url, payload);
  }

  protected ResultActions callPut(AuthenticatedUser authUser, String url, String payload)
      throws Exception {
    return buildAndExecRequest("PUT", null, authUser, url, payload);
  }

  protected ResultActions callDelete(String authToken, String url) throws Exception {
    return buildAndExecRequest("DELETE", authToken, null, url, null);
  }

  protected ResultActions callDelete(AuthenticatedUser authUser, String url) throws Exception {
    return buildAndExecRequest("DELETE", null, authUser, url, null);
  }

  private ResultActions buildAndExecRequest(
      String type, String authToken, AuthenticatedUser authUser, String url, String payload)
      throws Exception {
    var request = switch (type) {
      case "GET" -> get(url).accept(MediaType.APPLICATION_JSON);
      case "POST" -> post(url)
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON);
      case "PUT" -> put(url)
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON);
      case "DELETE" -> delete(url).accept(MediaType.APPLICATION_JSON);
      default -> throw new IllegalArgumentException("Invalid method type");
    };

    if (StringUtils.hasText(payload)) {
      request.content(payload);
    }

    if (Objects.nonNull(authToken)) {
      request.header(HttpHeaders.AUTHORIZATION, authToken);
    } else if (Objects.nonNull(authUser)) {
      request.with(user(authUser));
    }
    return mockMvc.perform(request);
  }
}
