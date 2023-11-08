package software.amazon.qbusiness.index;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DescribeIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.DescribeIndexResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import static software.amazon.qbusiness.index.Constants.API_GET_INDEX;

public class ReadHandler extends BaseHandlerStd {
  private Logger logger;

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    this.logger.log("[StackId: %s, ApplicationId: %s, IndexId: %s] Entering Read Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getIndexId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Index::Read", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                // Create Get Index request from resource model
                .translateToServiceRequest(Translator::translateToReadRequest)
                // Make call to the service
                .makeServiceCall(this::callGetIndex)
                .handleError((describeIndexRequest, error, client, model, context) ->
                    handleError(describeIndexRequest, model, error, context, logger, API_GET_INDEX))
                .done(serviceResponse -> ProgressEvent.progress(Translator.translateFromReadResponse(serviceResponse), callbackContext))
        )
        // Now process listing tags for the resource
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Index::ListTags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToListTagsRequest(request, model))
                .makeServiceCall(this::callListTags)
                .handleError((describeIndexRequest, error, client, model, context) ->
                    handleError(describeIndexRequest, model, error, context, logger, API_GET_INDEX))
                .done(listTagsResponse -> ProgressEvent.defaultSuccessHandler(
                        Translator.translateFromReadResponseWithTags(listTagsResponse, progress.getResourceModel())
                    )
                )
        );
  }

  private DescribeIndexResponse callGetIndex(final DescribeIndexRequest request, final ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::describeIndex);
  }
}
