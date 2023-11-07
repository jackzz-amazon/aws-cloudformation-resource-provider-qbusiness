package software.amazon.qbusiness.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.ApplicationStatus;
import software.amazon.awssdk.services.qbusiness.model.AppliedChatConfiguration;
import software.amazon.awssdk.services.qbusiness.model.ChatCapacityUnitConfiguration;
import software.amazon.awssdk.services.qbusiness.model.DescribeApplicationRequest;
import software.amazon.awssdk.services.qbusiness.model.DescribeApplicationResponse;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ResponseConfiguration;
import software.amazon.awssdk.services.qbusiness.model.ResponseControlStatus;
import software.amazon.awssdk.services.qbusiness.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.qbusiness.model.Tag;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandlerTest extends AbstractTestBase {

  private static final String APP_ID = "63451660-1596-4f1a-a3c8-e5f4b33d9fe5";

  @Mock
  private AmazonWebServicesClientProxy proxy;

  @Mock
  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  private QBusinessClient sdkClient;

  private AutoCloseable testMocks;

  private ReadHandler underTest;

  private ResourceHandlerRequest<ResourceModel> testRequest;
  private ResourceModel model;


  @BeforeEach
  public void setup() {
    testMocks = MockitoAnnotations.openMocks(this);
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    sdkClient = mock(QBusinessClient.class);
    proxyClient = MOCK_PROXY(proxy, sdkClient);
    underTest = new ReadHandler();

    model = ResourceModel.builder()
        .applicationId(APP_ID)
        .build();
    testRequest = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(model)
        .awsAccountId("123456")
        .awsPartition("aws")
        .region("us-east-1")
        .stackId("Stack1")
        .build();
  }

  @AfterEach
  public void tear_down() throws Exception {
    verify(sdkClient, atLeastOnce()).serviceName();
    verifyNoMoreInteractions(sdkClient);

    testMocks.close();
  }

  @Test
  public void handleRequest_SimpleSuccess() {
    // set up test scenario
    when(proxyClient.client().describeApplication(any(DescribeApplicationRequest.class)))
        .thenReturn(DescribeApplicationResponse.builder()
            .applicationId(APP_ID)
            .roleArn("role1")
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .description("this is a description, there are many like it but this one is mine.")
            .name("Foobar")
            .status(ApplicationStatus.ACTIVE)
            .capacityUnitConfiguration(ChatCapacityUnitConfiguration.builder()
                .users(10)
                .build())
            .chatConfiguration(AppliedChatConfiguration.builder()
                .responseConfiguration(ResponseConfiguration.builder()
                    .blockedPhrases("Guaranteed returns")
                    .blockedTopicsPrompt("What is nifty?")
                    .defaultMessage("Welcome! Take a seat by the hearth.")
                    .nonRetrievalResponseControlStatus(ResponseControlStatus.ENABLED)
                    .retrievalResponseControlStatus(ResponseControlStatus.ENABLED)
                    .build())
                .build())
            .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                .kmsKeyId("keyblade")
                .build())
            .build());
    when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
        .thenReturn(ListTagsForResourceResponse.builder()
            .tags(List.of(Tag.builder()
                .key("Category")
                .value("Chat Stuff")
                .build()))
            .build());

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify result
    verify(sdkClient).describeApplication(any(DescribeApplicationRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    assertThat(responseProgress).isNotNull();
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(responseProgress.getResourceModels()).isNull();
    assertThat(responseProgress.getMessage()).isNull();
    assertThat(responseProgress.getErrorCode()).isNull();
    ResourceModel resultModel = responseProgress.getResourceModel();
    assertThat(resultModel.getName()).isEqualTo("Foobar");
    assertThat(resultModel.getApplicationId()).isEqualTo(APP_ID);
    assertThat(resultModel.getRoleArn()).isEqualTo("role1");
    assertThat(resultModel.getCreatedAt()).isEqualTo("2023-10-20T18:02:15Z");
    assertThat(resultModel.getUpdatedAt()).isEqualTo("2023-10-20T22:02:15Z");
    assertThat(resultModel.getDescription()).isEqualTo("this is a description, there are many like it but this one is mine.");
    assertThat(resultModel.getStatus()).isEqualTo(ApplicationStatus.ACTIVE.toString());
    assertThat(resultModel.getCapacityUnitConfiguration().getUsers()).isEqualTo(10);
    assertThat(resultModel.getChatConfiguration()).isEqualTo(ChatConfiguration.builder()
        .responseConfiguration(software.amazon.qbusiness.application.ResponseConfiguration.builder()
            .blockedPhrases(List.of("Guaranteed returns"))
            .blockedTopicsPrompt("What is nifty?")
            .defaultMessage("Welcome! Take a seat by the hearth.")
            .nonRetrievalResponseControlStatus("ENABLED")
            .retrievalResponseControlStatus("ENABLED")
            .build())
        .build());
    assertThat(resultModel.getServerSideEncryptionConfiguration().getKmsKeyId()).isEqualTo("keyblade");

    var tags = resultModel.getTags().stream().map(tag -> Map.entry(tag.getKey(), tag.getValue())).toList();
    assertThat(tags).isEqualTo(List.of(
        Map.entry("Category", "Chat Stuff")
    ));
  }

  @Test
  public void handleRequest_SimpleSuccess_withMissingProperties() {
    // set up test scenario
    when(proxyClient.client().describeApplication(any(DescribeApplicationRequest.class)))
        .thenReturn(DescribeApplicationResponse.builder()
            .applicationId(APP_ID)
            .roleArn("role1")
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .description("desc")
            .name("Foobar")
            .status(ApplicationStatus.ACTIVE)
            .chatConfiguration(AppliedChatConfiguration.builder()
                .build())
            .build());

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify result
    verify(sdkClient).describeApplication(any(DescribeApplicationRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    assertThat(responseProgress).isNotNull();
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(responseProgress.getResourceModels()).isNull();
    assertThat(responseProgress.getMessage()).isNull();
    assertThat(responseProgress.getErrorCode()).isNull();

    ResourceModel resultModel = responseProgress.getResourceModel();
    assertThat(resultModel.getChatConfiguration()).isNull();
    assertThat(resultModel.getServerSideEncryptionConfiguration()).isNull();
    assertThat(resultModel.getCapacityUnitConfiguration()).isNull();
  }

  private static Stream<Arguments> serviceErrorAndExpectedCfnCode() {
    return Stream.of(
        Arguments.of(ValidationException.builder().message("nopes").build(), HandlerErrorCode.InvalidRequest),
        Arguments.of(ResourceNotFoundException.builder().message("404").build(), HandlerErrorCode.NotFound),
        Arguments.of(ThrottlingException.builder().message("too much").build(), HandlerErrorCode.Throttling),
        Arguments.of(AccessDeniedException.builder().message("denied!").build(), HandlerErrorCode.AccessDenied),
        Arguments.of(InternalServerException.builder().message("something happened").build(), HandlerErrorCode.GeneralServiceException)
    );
  }

  @ParameterizedTest
  @MethodSource("serviceErrorAndExpectedCfnCode")
  public void testThatItReturnsExpectedErrorCode(QBusinessException serviceError, HandlerErrorCode cfnErrorCode) {
    when(proxyClient.client().describeApplication(any(DescribeApplicationRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).describeApplication(any(DescribeApplicationRequest.class));
    assertThat(responseProgress.getErrorCode()).isEqualTo(cfnErrorCode);
    assertThat(responseProgress.getResourceModels()).isNull();
  }

  @ParameterizedTest
  @MethodSource("serviceErrorAndExpectedCfnCode")
  public void testThatItReturnsExpectedErrorCodeWhenListTagsForResourceFails(QBusinessException serviceError, HandlerErrorCode cfnErrorCode) {
    // set up test scenario
    when(proxyClient.client().describeApplication(any(DescribeApplicationRequest.class)))
        .thenReturn(DescribeApplicationResponse.builder()
            .applicationId(APP_ID)
            .roleArn("role1")
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .description("desc")
            .name("Foobar")
            .status(ApplicationStatus.ACTIVE)
            .build());

    when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).describeApplication(any(DescribeApplicationRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    assertThat(responseProgress.getErrorCode()).isEqualTo(cfnErrorCode);
    assertThat(responseProgress.getResourceModels()).isNull();
  }

}
