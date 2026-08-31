function Get-CommandOutputLine {
    param([scriptblock]$Command)

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $Command 2>&1
        $line = $output |
            ForEach-Object { [string]$_ } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            Select-Object -First 1
        return $line -as [string]
    } catch {
        return "unavailable: $($_.Exception.Message)"
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function New-TestCaseMetadata {
    param(
        [string]$InterfaceName,
        [string]$Scenario,
        [string]$Method,
        [string]$Url,
        [string]$Request,
        [string]$Expected,
        [string]$Assertions
    )

    [pscustomobject]@{
        InterfaceName = $InterfaceName
        Scenario = $Scenario
        Method = $Method
        Url = $Url
        Request = $Request
        Expected = $Expected
        Assertions = $Assertions
    }
}

function Get-TestCaseMetadata {
    param([object]$Case)

    $service = if ([string]::IsNullOrWhiteSpace($Case.Service)) { "service" } else { $Case.Service }
    $classShort = if (-not [string]::IsNullOrWhiteSpace($Case.ClassName)) {
        ($Case.ClassName -split '\.')[-1]
    } else {
        "TestCase"
    }
    $method = if (-not [string]::IsNullOrWhiteSpace($Case.MethodName)) { $Case.MethodName } else { $Case.DisplayName }
    $key = "$service.$classShort.$method"

    switch -Regex ($key) {
        '^api-gateway\.GatewayRouteDefinitionTests\.authRoutesAreSplitByOwnedService$' {
            return New-TestCaseMetadata "Gateway auth route definitions" "Authentication routes are owned by user, merchant, and fulfillment services" "ROUTE" "/api/auth/login; /api/auth/register; /api/auth/admin/**; /api/auth/merchant/**; /api/auth/rider/**" "Gateway route definition lookup" "Routes point to lb://user-service, lb://merchant-service, and lb://fulfillment-service" "Route ids and Path predicates match expected service owners"
        }
        '^api-gateway\.GatewayRouteDefinitionTests\.userReviewRouteHasPriorityOverUserServiceFallback$' {
            return New-TestCaseMetadata "Gateway review route priority" "User review route must reach engagement-service before /api/user/** fallback" "ROUTE" "/api/user/reviews" "Gateway route order lookup" "engagement-user-reviews order is before user-service fallback" "Route URI is lb://engagement-service and priority is higher than fallback"
        }
        '^api-gateway\.GatewayRouteDefinitionTests\.roleAndDomainRoutesTargetExpectedServices$' {
            return New-TestCaseMetadata "Gateway domain route definitions" "Role and domain paths are routed to the correct microservices" "ROUTE" "/api/rider/**; /api/delivery/**; /api/admin/riders/**; /api/reviews/**; /api/messages/**; /api/uploads/**; /api/admin/orders/**; /api/coupons/**; /api/orders/*/pay" "Gateway route definition lookup" "Fulfillment, engagement, order, and settlement routes target their service owners" "Route ids, URI targets, and Path predicates match expected services"
        }

        '^merchant-service\.MerchantServiceApiTests\.merchantRegisterThenLoginReturnsMerchantToken$' {
            return New-TestCaseMetadata "Merchant registration and login APIs" "New merchant registers and then logs in" "POST; POST" "/api/auth/merchant/register; /api/auth/merchant/login" "Body: {username:newmerchant, phone:13900000011, password:123456, nickname:New Merchant}; then {username:newmerchant, password:123456}" "code=200; accessToken exists; role=merchant; status=pending" "HTTP 200, business code 200, token and merchant user fields are returned"
        }
        '^merchant-service\.MerchantServiceApiTests\.merchantLoginAcceptsSeedAccountAndReturnsMerchantId$' {
            return New-TestCaseMetadata "Merchant login API" "Seed merchant account can log in" "POST" "/api/auth/merchant/login" "Body: {username:merchant1, password:123456}" "code=200; user.id=20001; merchantId=20001; role=merchant" "Login response contains merchant identity and role"
        }
        '^merchant-service\.MerchantServiceApiTests\.merchantLoginRejectsBadPasswordAndFrozenMerchant$' {
            return New-TestCaseMetadata "Merchant login API" "Reject bad password and frozen merchant login" "POST" "/api/auth/merchant/login" "Bodies: wrong password; frozen merchant credentials" "code=400 for both invalid credential and frozen account cases" "Error responses contain expected business codes and messages"
        }
        '^merchant-service\.MerchantServiceApiTests\.merchantSelfDashboardProfileAndProductsWorkWithMerchantToken$' {
            return New-TestCaseMetadata "Merchant workspace APIs" "Merchant token reads dashboard, profile, product list, and product detail" "GET" "/api/merchant/dashboard; /api/merchant/profile; /api/merchant/products; /api/merchant/products/{productId}" "Header: Authorization=merchant token" "code=200; dashboard stats, merchant profile, products, and product detail are returned" "All merchant-owned workspace endpoints return expected data"
        }
        '^merchant-service\.MerchantServiceApiTests\.merchantRegisterRejectsDuplicatePhone$' {
            return New-TestCaseMetadata "Merchant registration API" "Reject merchant registration with duplicated phone" "POST" "/api/auth/merchant/register" "Body: {username:merchant-dupe, phone:13800138002, password:123456}" "code=400; duplicate phone is rejected" "Business error is returned for duplicated merchant phone"
        }
        '^merchant-service\.MerchantServiceApiTests\.merchantListOnlyReturnsActiveMerchants$' {
            return New-TestCaseMetadata "Merchant listing API" "Public merchant list hides inactive merchants" "GET" "/api/merchants" "Query: default paging/filter parameters" "code=200; total=1; active merchant appears" "Only active public merchant data is returned"
        }
        '^merchant-service\.MerchantServiceApiTests\.searchMatchesProductNameAndHidesInactiveMerchants$' {
            return New-TestCaseMetadata "Search API" "Search by product name and hide inactive merchants" "GET" "/api/search?keyword=Hidden%20Rice; /api/search?keyword=Braised" "Query: keyword" "Hidden inactive merchant returns empty; active product search returns merchant and product" "Search results respect product keyword and merchant active status"
        }
        '^merchant-service\.MerchantServiceApiTests\.quoteCalculatesSpecPriceAndStock$' {
            return New-TestCaseMetadata "Product quote internal API" "Order service quotes product with spec price and stock" "POST" "/internal/products/quote" "Body: {requestId:order-quote-1, merchantId:20001, items:[{productId:30001, specLabel:Large, quantity:2}]}" "code=200; available=true; unitPrice=25.00; totalAmount=50.00" "Quote response returns available stock, spec price, and total amount"
        }
        '^merchant-service\.MerchantServiceApiTests\.quoteReportsInsufficientStock$' {
            return New-TestCaseMetadata "Product quote internal API" "Quote reports insufficient stock" "POST" "/internal/products/quote" "Body: {requestId:order-quote-2, merchantId:20001, items:[{productId:30002, quantity:1}]}" "code=200; available=false; stockEnough=false" "Quote response reports insufficient stock without reserving inventory"
        }
        '^merchant-service\.MerchantServiceApiTests\.reserveAndReleaseRestoreStock$' {
            return New-TestCaseMetadata "Inventory reserve and release internal APIs" "Reserve stock and release it back" "POST; POST" "/internal/products/reserve; /internal/products/release" "Body: {requestId:stock-change-1, merchantId:20001, items:[{productId:30001, specLabel:Large, quantity:2}]}" "reserve status=reserved with reduced stock; release status=released with restored stock" "Stock rows and response remainingStock values reflect reserve and release"
        }
        '^merchant-service\.MerchantServiceApiTests\.reserveRejectsInsufficientStock$' {
            return New-TestCaseMetadata "Inventory reserve internal API" "Reserve rejects insufficient stock and exposes failed change status" "POST; GET" "/internal/products/reserve; /internal/products/changes/{requestId}" "Body: {requestId:stock-change-2, merchantId:20001, items:[{productId:30002, quantity:1}]}" "reserve code=400; change status=failed; stock remains unchanged" "Failed stock change is recorded and can be queried"
        }

        '^user-service\.UserServiceApiTests\.consumerRegisterThenLoginReturnsConsumerToken$' {
            return New-TestCaseMetadata "Consumer registration and login APIs" "New consumer registers and then logs in" "POST; POST" "/api/auth/register; /api/auth/login" "Body: {username:newuser, phone:13900000001, password:123456, nickname:New User}; then {username:newuser, password:123456}" "code=200; accessToken exists; role=consumer; nickname=New User" "Registration and login return valid consumer identity"
        }
        '^user-service\.UserServiceApiTests\.consumerRegisterRejectsDuplicatePhone$' {
            return New-TestCaseMetadata "Consumer registration API" "Reject duplicated consumer phone" "POST" "/api/auth/register" "Body: {username:dupe, phone:13800138001, password:123456}" "code=400; duplicate phone is rejected" "Business error is returned for duplicated phone"
        }
        '^user-service\.UserServiceApiTests\.adminLoginReturnsAdminToken$' {
            return New-TestCaseMetadata "Admin login API" "Admin account logs in" "POST" "/api/auth/admin/login" "Body: {username:admin, password:admin123}" "code=200; accessToken exists; role=admin" "Admin token and role are returned"
        }
        '^user-service\.UserServiceApiTests\.adminCanListFreezeAndUnfreezeConsumers$' {
            return New-TestCaseMetadata "Admin user management APIs" "Admin lists, freezes, and unfreezes consumers" "GET; DELETE; PUT" "/api/admin/users; /api/admin/users/{userId}; /api/admin/users/{userId}/unfreeze" "Header: Authorization=admin token; Query: page=1&pageSize=20" "list code=200; freeze status=frozen; unfreeze status=active" "Admin-only user state transitions work"
        }
        '^user-service\.UserServiceApiTests\.consumerCannotUseAdminUserApi$' {
            return New-TestCaseMetadata "Admin user management API" "Consumer token is rejected from admin user API" "GET" "/api/admin/users" "Header: Authorization=consumer token" "code=403" "Role guard blocks consumer access to admin endpoint"
        }
        '^user-service\.UserServiceApiTests\.frozenConsumerTokenCannotAccessUserApis$' {
            return New-TestCaseMetadata "Consumer authorization guard" "Frozen consumer cannot use profile or cart APIs" "DELETE; GET; GET" "/api/admin/users/{userId}; /api/user/profile; /api/user/cart" "Freeze user with admin token, then call user APIs with old consumer token" "code=403 for user profile and cart after freeze" "Runtime status guard rejects frozen consumer token"
        }
        '^user-service\.UserServiceApiTests\.getProfileReturnsCurrentConsumer$' {
            return New-TestCaseMetadata "Consumer profile API" "Consumer reads own profile" "GET" "/api/user/profile" "Header: Authorization=consumer token" "code=200; id=10001; username=demo; role=consumer" "Profile response matches current consumer identity"
        }
        '^user-service\.UserServiceApiTests\.updateProfileRejectsDuplicatePhone$' {
            return New-TestCaseMetadata "Consumer profile API" "Reject profile update with another user's phone" "PUT" "/api/user/profile" "Header: Authorization=consumer token; Body: {phone:13800138099}" "code=400" "Duplicate phone validation blocks update"
        }
        '^user-service\.UserServiceApiTests\.addingDefaultAddressClearsPreviousDefaultAddress$' {
            return New-TestCaseMetadata "Consumer address APIs" "Adding a new default address clears old default address" "POST; GET" "/api/user/addresses; /api/user/addresses" "Header: Authorization=consumer token; Body: {name, phone, detail, isDefault:true}" "created address is default; old default becomes false" "Address list reflects exactly one default address"
        }
        '^user-service\.UserServiceApiTests\.addCartUsesMerchantServiceQuoteAndCalculatesSubtotal$' {
            return New-TestCaseMetadata "Cart API" "Add cart item using merchant quote data" "POST" "/api/user/cart" "Header: Authorization=consumer token; Body: {merchantId:20001, productId:30003, quantity:2, specLabel:Large}" "code=200; merchant/product snapshot present; subtotal=50.00" "Cart item uses merchant-service quote and calculates subtotal"
        }
        '^user-service\.UserServiceApiTests\.addExistingCartItemIncrementsQuantityWithoutNewQuote$' {
            return New-TestCaseMetadata "Cart API" "Adding existing cart item increments quantity" "POST" "/api/user/cart" "Header: Authorization=consumer token; Body: {merchantId:20001, productId:30001, quantity:2}" "existing cart id is reused; quantity=3; subtotal=66.00" "Existing cart row is incremented instead of duplicated"
        }
        '^user-service\.UserServiceApiTests\.settingCartQuantityToZeroDeletesItem$' {
            return New-TestCaseMetadata "Cart API" "Setting cart item quantity to zero deletes item" "PUT; GET" "/api/user/cart/{cartId}?quantity=0; /api/user/cart" "Header: Authorization=consumer token" "update code=200 with empty data; cart list becomes empty" "Zero quantity removes the cart item"
        }
        '^user-service\.UserServiceApiTests\.favoriteRejectsInactiveMerchantFromMerchantService$' {
            return New-TestCaseMetadata "Favorite merchant API" "Reject favorite for inactive merchant snapshot" "POST" "/api/user/favorites/{merchantId}" "Header: Authorization=consumer token; merchant snapshot status=closed" "code=400" "Cross-service merchant status validation blocks inactive favorite"
        }
        '^user-service\.UserServiceApiTests\.cartReturnsServiceUnavailableWhenMerchantServiceFails$' {
            return New-TestCaseMetadata "Cart API" "Cart query propagates merchant-service failure" "GET" "/api/user/cart" "Header: Authorization=consumer token; merchant client throws service unavailable" "code=503; service unavailable message returned" "Cross-service failure is surfaced as business 503"
        }
        '^user-service\.UserServiceApiTests\.internalClearCartByMerchantOnlyDeletesOwnedMerchantItems$' {
            return New-TestCaseMetadata "Cart internal API" "Order service clears cart items owned by one merchant" "DELETE; GET" "/internal/users/{userId}/cart?merchantId={merchantId}; /api/user/cart" "Path userId=10001; Query merchantId=20001" "clear code=200; owned merchant cart items removed" "Internal clear-cart endpoint deletes only scoped merchant items"
        }
        '^user-service\.UserServiceUnitTests\.getProfileThrowsNotFoundWhenUserMissing$' {
            return New-TestCaseMetadata "UserService.getProfile" "Missing user profile throws not found business error" "SERVICE" "service:user-service/UserService.getProfile" "Call getProfile(10001) with userMapper returning null" "BusinessException code=404" "Service layer throws not found when user row is absent"
        }
        '^user-service\.UserServiceUnitTests\.updateProfileRejectsPhoneUsedByAnotherUser$' {
            return New-TestCaseMetadata "UserService.updateProfile" "Reject profile update when phone belongs to another user" "SERVICE" "service:user-service/UserService.updateProfile" "Call updateProfile(10001, phone=13800138099) with duplicate count=1" "BusinessException code=400" "Service layer duplicate phone validation fires"
        }
        '^user-service\.UserServiceUnitTests\.frozenUserCannotUpdateProfileWithOldToken$' {
            return New-TestCaseMetadata "UserService.updateProfile" "Frozen user cannot update profile with old token identity" "SERVICE" "service:user-service/UserService.updateProfile" "Call updateProfile(10001, nickname=New Name) with user status=frozen" "BusinessException code=403" "Service layer active-user guard rejects frozen account"
        }

        '^fulfillment-service\.FulfillmentServiceApiTests\.riderRegisterThenLoginReturnsRiderToken$' {
            return New-TestCaseMetadata "Rider registration and login APIs" "New rider registers and then logs in" "POST; POST" "/api/auth/rider/register; /api/auth/rider/login" "Body: {username:newrider, phone:13900000002, password:123456, nickname:New Rider}; then {username:newrider, password:123456}" "code=200; accessToken exists; role=rider; status=pending" "Rider registration and login return valid pending rider identity"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.riderRegisterRejectsDuplicateUsername$' {
            return New-TestCaseMetadata "Rider registration API" "Reject duplicated rider username" "POST" "/api/auth/rider/register" "Body: {username:rider01, phone:13900000002, password:123456}" "code=400" "Duplicate username validation blocks rider registration"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.riderRegisterRejectsDuplicatePhone$' {
            return New-TestCaseMetadata "Rider registration API" "Reject duplicated rider phone" "POST" "/api/auth/rider/register" "Body: {username:duperider, phone:13800138004, password:123456}" "code=400" "Duplicate phone validation blocks rider registration"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.adminCanListAuditFreezeAndUnfreezeRiders$' {
            return New-TestCaseMetadata "Admin rider management APIs" "Admin lists, audits, freezes, and unfreezes riders" "GET; PUT; DELETE; PUT" "/api/admin/riders; /api/admin/riders/{riderId}/audit; /api/admin/riders/{riderId}; /api/admin/riders/{riderId}/unfreeze" "Header: Authorization=admin token; audit Body: {status:active, opinion:pass}" "list code=200; audit active; freeze frozen; unfreeze active" "Admin rider workflow changes rider status as expected"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.riderDashboardSummarizesTodayCompletedTasks$' {
            return New-TestCaseMetadata "Rider dashboard API" "Rider dashboard summarizes today's completed tasks" "GET" "/api/rider/dashboard" "Header: Authorization=rider token; order-service completed tasks mocked" "code=200; todayDeliveries=2; todayEarnings=10.0; status=active" "Dashboard aggregates order-service task snapshots"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.frozenRiderCannotViewDashboardOrTaskLists$' {
            return New-TestCaseMetadata "Rider authorization guard" "Pending/frozen rider cannot view dashboard or tasks" "GET; GET" "/api/rider/dashboard; /api/rider/tasks" "Header: Authorization=rider token for non-active rider" "code=403 for both endpoints" "Active-rider guard blocks restricted endpoints"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.getRiderProfileReturnsCurrentRider$' {
            return New-TestCaseMetadata "Rider profile API" "Rider reads own profile" "GET" "/api/rider/profile" "Header: Authorization=rider token" "code=200; id=40001; name=rider01; status=active" "Profile response matches current rider"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.updateRiderProfileRejectsDuplicatePhone$' {
            return New-TestCaseMetadata "Rider profile API" "Reject rider profile update with another rider's phone" "PUT" "/api/rider/profile" "Header: Authorization=rider token; Body: {phone:13800138005}" "code=400" "Duplicate phone validation blocks rider update"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.getTasksCombinesOrderTasksAndMerchantSnapshots$' {
            return New-TestCaseMetadata "Rider task list API" "Task list combines available, assigned, completed orders and merchant snapshots" "GET" "/api/rider/tasks" "Header: Authorization=rider token; order-service and merchant-service snapshots mocked" "code=200; grouped tasks and stats returned" "Task list merges remote order and merchant data"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.getTasksTreatsNullRemoteListsAsEmptyLists$' {
            return New-TestCaseMetadata "Rider task list API" "Null remote order lists are treated as empty lists" "GET" "/api/rider/tasks" "Header: Authorization=rider token; remote task lists return null" "code=200; available/assigned/completed are empty; stats zero" "Null remote data is normalized safely"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.getTasksRendersPartialTaskSnapshotWithoutFailing$' {
            return New-TestCaseMetadata "Rider task list API" "Partial order snapshots render without failing" "GET" "/api/rider/tasks" "Header: Authorization=rider token; partial task snapshot contains null fields/items" "code=200; unknown status/item fallback text returned" "Partial remote data uses fallback rendering"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.acceptTaskAssignsActiveRiderThroughOrderService$' {
            return New-TestCaseMetadata "Rider task update API" "Active rider accepts task through order service" "PUT" "/api/rider/tasks/{orderId}" "Header: Authorization=rider token; Body: {status:pending_accept}" "code=200; status=delivering; destination returned" "Order-service assignRider is called and response is mapped"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.frozenRiderCannotUpdateTask$' {
            return New-TestCaseMetadata "Rider task update API" "Frozen/pending rider cannot update task status" "PUT" "/api/rider/tasks/{orderId}" "Header: Authorization=non-active rider token; Body: {status:pending_accept}" "code=403" "Active-rider guard rejects task update"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.deliveryInfoOnlyAllowsOrderOwner$' {
            return New-TestCaseMetadata "Delivery detail API" "Delivery detail is visible only to order owner" "GET" "/api/delivery/{orderId}" "Header: Authorization=consumer token for owner and non-owner" "owner code=200; non-owner code=403" "Order ownership is enforced for delivery detail"
        }
        '^fulfillment-service\.FulfillmentServiceApiTests\.internalRiderSnapshotReturnsNotFoundForMissingRider$' {
            return New-TestCaseMetadata "Rider internal snapshot API" "Missing rider snapshot returns not found" "GET" "/internal/riders/{riderId}" "Path riderId=49999" "code=404" "Internal rider lookup reports missing rider"
        }
        '^fulfillment-service\.RiderServiceUnitTests\.frozenRiderCannotAcceptTaskAndDoesNotCallOrderService$' {
            return New-TestCaseMetadata "RiderService.updateTaskStatus" "Frozen rider cannot accept task and order service is not called" "SERVICE" "service:fulfillment-service/RiderService.updateTaskStatus" "Call updateTaskStatus with frozen rider and pending_accept action" "BusinessException code=403; no remote assign call" "Service guard rejects inactive rider before remote call"
        }
        '^fulfillment-service\.RiderServiceUnitTests\.activeRiderRejectsUnsupportedTaskStatus$' {
            return New-TestCaseMetadata "RiderService.updateTaskStatus" "Active rider rejects unsupported task status" "SERVICE" "service:fulfillment-service/RiderService.updateTaskStatus" "Call updateTaskStatus with unsupported status" "BusinessException code=400" "Unsupported task status is rejected"
        }

        '^engagement-service\.EngagementServiceApiTests\.submitReviewCreatesReviewsAndMarksOrderItemsReviewed$' {
            return New-TestCaseMetadata "Review submission API" "Consumer submits review and reviewed order items are marked" "POST" "/api/reviews" "Header: Authorization=consumer token; Body: {orderId:70001, items:[{productId:30001, rating:5, content:Tasty, images:[...]}]}" "code=200; one review returned; review event published; order items marked reviewed" "Review data is enriched and cross-service side effects are invoked"
        }
        '^engagement-service\.EngagementServiceApiTests\.submitReviewRejectsDuplicateOrderReview$' {
            return New-TestCaseMetadata "Review submission API" "Reject duplicate order review" "POST" "/api/reviews" "Header: Authorization=consumer token; Body: {orderId:70002, items:[...]}" "code=400" "Duplicate order review is blocked"
        }
        '^engagement-service\.EngagementServiceApiTests\.submitReviewRejectsUnfinishedOrder$' {
            return New-TestCaseMetadata "Review submission API" "Reject review for unfinished order" "POST" "/api/reviews" "Header: Authorization=consumer token; Body: {orderId:70003, items:[...]}" "code=400" "Only completed orders can be reviewed"
        }
        '^engagement-service\.EngagementServiceApiTests\.submitReviewRejectsProductOutsideOrder$' {
            return New-TestCaseMetadata "Review submission API" "Reject review item whose product is not in the order" "POST" "/api/reviews" "Header: Authorization=consumer token; Body: {orderId:70001, items:[{productId:39999, rating:5}]}" "code=400" "Product ownership inside order is validated"
        }
        '^engagement-service\.EngagementServiceApiTests\.getProductReviewsEnrichesUserAndProductSnapshots$' {
            return New-TestCaseMetadata "Product reviews API" "Product reviews include user and product snapshots" "GET" "/api/products/{productId}/reviews" "Path productId=30001" "code=200; content, userName, and productName returned" "Review list is enriched from user and product services"
        }
        '^engagement-service\.EngagementServiceApiTests\.getMerchantReviewsRatingAndCurrentUserReviews$' {
            return New-TestCaseMetadata "Merchant and user review APIs" "Read merchant reviews, rating, and current user's reviews" "GET" "/api/merchants/{merchantId}/reviews; /api/merchants/{merchantId}/rating; /api/user/reviews" "Path merchantId=20001; Header: Authorization=consumer token for user reviews" "code=200; merchant reviews, rating=4.0, and current user reviews returned" "Read APIs expose expected review projections"
        }
        '^engagement-service\.EngagementServiceApiTests\.uploadImagesAcceptsImagesAndRejectsNonImagesAndOversizedFiles$' {
            return New-TestCaseMetadata "Image upload API" "Accept valid image upload and reject invalid files" "MULTIPART POST" "/api/uploads/images" "Header: Authorization=consumer token; multipart files: image/png, text/plain, oversized image" "valid image code=200; text and oversized files code=400" "Upload validation accepts image and rejects bad content type/size"
        }
        '^engagement-service\.EngagementServiceApiTests\.sendMessageRequiresValidOrderParticipantAndPublishesEvent$' {
            return New-TestCaseMetadata "Message send API" "Consumer sends message to order merchant and event is published" "POST" "/api/messages" "Header: Authorization=consumer token; Body: {receiverId:20001, receiverType:merchant, orderId:70001, content:Please add napkins}" "code=200; sender/receiver/content returned; message event published" "Order participant validation and event publish pass"
        }
        '^engagement-service\.EngagementServiceApiTests\.merchantCanReplyToUserInOrderConversation$' {
            return New-TestCaseMetadata "Message send API" "Merchant replies to user in order conversation" "POST" "/api/messages" "Header: Authorization=merchant token; Body: {receiverId:10001, receiverType:user, orderId:70001, content:We are preparing your meal}" "code=200; senderType=merchant; receiverType=user" "Merchant participant can send order-scoped message"
        }
        '^engagement-service\.EngagementServiceApiTests\.riderCanMessageOrderUserWhenRiderIsParticipant$' {
            return New-TestCaseMetadata "Message send API" "Rider messages user when rider is order participant" "POST" "/api/messages" "Header: Authorization=rider token; Body: {receiverId:10001, receiverType:user, orderId:70001, content:I am on the way}" "code=200; senderType=rider; receiverType=user" "Rider participant can send order-scoped message"
        }
        '^engagement-service\.EngagementServiceApiTests\.sendMessageRejectsReceiverOutsideOrderParticipants$' {
            return New-TestCaseMetadata "Message send API" "Reject receiver outside order participants" "POST" "/api/messages" "Header: Authorization=consumer token; Body: {receiverId:29999, receiverType:merchant, orderId:70001, content:hello}" "code=400" "Order participant validation rejects unrelated receiver"
        }
        '^engagement-service\.EngagementServiceApiTests\.sendMessageRejectsMissingOrderIdAndSelfSend$' {
            return New-TestCaseMetadata "Message send API" "Reject missing order id and self-send" "POST" "/api/messages" "Header: Authorization=consumer token; Bodies: missing orderId; receiver is same user" "code=400 for both cases" "Message validation requires order and blocks self-send"
        }
        '^engagement-service\.EngagementServiceApiTests\.threadsReportUnreadMessageAndTargetSnapshot$' {
            return New-TestCaseMetadata "Message thread API" "Conversation threads report unread count and target snapshot" "GET" "/api/messages/threads" "Header: Authorization=consumer token" "code=200; targetId, targetType, targetName, orderNo, unreadCount returned" "Thread projection contains target and unread metadata"
        }
        '^engagement-service\.EngagementServiceApiTests\.readingMessagesMarksUnreadConversationAsRead$' {
            return New-TestCaseMetadata "Message read APIs" "Reading messages marks unread conversation as read" "GET; GET; GET" "/api/messages/unread-count; /api/messages?targetId=20001&targetType=merchant&orderId=70001; /api/messages/unread-count" "Header: Authorization=consumer token; Query: targetId, targetType, orderId" "unread count changes from 1 to 0 after reading messages" "Message read endpoint marks conversation as read"
        }
        '^engagement-service\.ReviewServiceUnitTests\.uploadImagesRejectsEmptyFileList$' {
            return New-TestCaseMetadata "ReviewService.uploadImages" "Reject empty image upload list" "SERVICE" "service:engagement-service/ReviewService.uploadImages" "Call uploadImages with empty file list" "BusinessException code=400" "Empty file list is rejected before storage"
        }
        '^engagement-service\.ReviewServiceUnitTests\.uploadImagesRejectsUnsafeSceneName$' {
            return New-TestCaseMetadata "ReviewService.uploadImages" "Reject unsafe image upload scene name" "SERVICE" "service:engagement-service/ReviewService.uploadImages" "Call uploadImages with unsafe scene name" "BusinessException code=400" "Unsafe scene input is rejected"
        }
        '^engagement-service\.ReviewServiceUnitTests\.uploadImagesStoresImageUnderRequestedScene$' {
            return New-TestCaseMetadata "ReviewService.uploadImages" "Store uploaded image under requested scene folder" "SERVICE" "service:engagement-service/ReviewService.uploadImages" "Call uploadImages with png file and scene=chat" "Returned path starts with /uploads/chat/" "Image storage path uses requested safe scene"
        }

        '^order-service\.OrderServiceApiTests\.consumerPublicApisExposeOrdersCheckoutCancelAndComplete$' {
            return New-TestCaseMetadata "Consumer order APIs" "Consumer lists orders, reads detail, checks out, cancels, and completes order" "GET; GET; POST; POST; POST" "/api/orders; /api/orders/{orderId}; /api/checkout; /api/orders/{orderId}/cancel; /api/orders/{orderId}/complete" "Header: Authorization=consumer token; checkout Body: {merchantId:20001, address:No. 3 Dorm, items:[{productId:30001, quantity:1}]}" "code=200 across flow; checkout creates order/item; cancel and complete states returned" "Consumer order workflow and persistence side effects pass"
        }
        '^order-service\.OrderServiceApiTests\.merchantPublicApisExposeMerchantOrdersAndUpdates$' {
            return New-TestCaseMetadata "Merchant order APIs" "Merchant lists owned orders and updates order status" "GET; PUT" "/api/merchant/orders; /api/merchant/orders/{orderId}" "Header: Authorization=merchant token; Body: {status:completed}" "code=200; merchant order appears; status becomes completed" "Merchant order visibility and update work"
        }
        '^order-service\.OrderServiceApiTests\.adminPublicApisExposeOrderListAndDetail$' {
            return New-TestCaseMetadata "Admin order APIs" "Admin lists orders and reads order detail" "GET; GET" "/api/admin/orders; /api/admin/orders/{orderId}" "Header: Authorization=admin token; Query: page=1&pageSize=20" "code=200; total=2; detail orderNo matches seed order" "Admin order read APIs return expected data"
        }
        '^order-service\.OrderServiceInternalApiTests\.getInternalOrderReturnsSnapshotAndItems$' {
            return New-TestCaseMetadata "Order internal snapshot API" "Internal order snapshot returns order and item data" "GET" "/internal/orders/{orderId}" "Path orderId=70002" "code=200; status=pending_payment; item name returned" "Internal order snapshot includes order status and items"
        }
        '^order-service\.OrderServiceInternalApiTests\.markPaidMovesPendingPaymentOrderToPendingAccept$' {
            return New-TestCaseMetadata "Order mark-paid internal API" "Settlement service marks pending payment order as paid" "POST" "/internal/orders/{orderId}/mark-paid" "Body: {transactionId:pay-70001, payMethod:mock, amount:27.00}" "code=200; status=pending_accept; paidAt present" "Payment confirmation moves order state and records paid time"
        }
        '^order-service\.OrderServiceInternalApiTests\.markPaidRejectsAmountMismatch$' {
            return New-TestCaseMetadata "Order mark-paid internal API" "Reject mark-paid when amount mismatches order amount" "POST" "/internal/orders/{orderId}/mark-paid" "Body: {transactionId:pay-70002, payMethod:mock, amount:1.00}" "code=400" "Amount mismatch is rejected"
        }
        '^order-service\.OrderServiceInternalApiTests\.checkoutReleasesInventoryAndRecordsCompensationWhenCouponLockFails$' {
            return New-TestCaseMetadata "OrderService.checkout compensation" "Checkout releases reserved inventory and records compensation when coupon lock fails" "SERVICE" "service:order-service/OrderService.checkout" "Call checkout with couponId=60001 while settlement lock throws" "No order/item created; compensation record resolved for merchant-service" "Inventory release compensation is recorded after coupon lock failure"
        }
        '^order-service\.OrderServiceInternalApiTests\.checkoutRejectsWhenCouponLockResponseIsNotConfirmed$' {
            return New-TestCaseMetadata "OrderService.checkout compensation" "Checkout rejects unconfirmed coupon lock response and rolls back inventory" "SERVICE" "service:order-service/OrderService.checkout" "Call checkout with coupon lock status=processing" "No order created; release_inventory compensation resolved" "Unconfirmed coupon lock triggers inventory rollback"
        }
        '^order-service\.OrderServiceInternalApiTests\.checkoutRejectsWhenInventoryResponseIsStillProcessing$' {
            return New-TestCaseMetadata "OrderService.checkout compensation" "Checkout rejects processing inventory response and records pending compensation" "SERVICE" "service:order-service/OrderService.checkout" "Call checkout with merchant reserve status=processing" "No order created; compensation status=pending" "Unconfirmed inventory reserve is captured for follow-up compensation"
        }
        '^order-service\.OrderServiceInternalApiTests\.cancelOrderReleasesInventoryAndKeepsCancellationWhenCouponReleaseFails$' {
            return New-TestCaseMetadata "OrderService.cancelOrder compensation" "Cancel order releases inventory and records coupon-release compensation failure" "SERVICE" "service:order-service/OrderService.cancelOrder" "Call cancelOrder for pending payment order with stockReserved=true and couponId=60001" "Order stays cancelled; stockReserved=false; release_coupon compensation recorded" "Cancellation remains successful while failed coupon release is tracked"
        }

        '^settlement-service\.SettlementServiceApiTests\.couponApisExposeUserCouponsAvailableCouponsAndClaimFlow$' {
            return New-TestCaseMetadata "Coupon APIs" "User reads own coupons, reads available coupons, and claims a coupon" "GET; GET; POST" "/api/coupons; /api/coupons/available; /api/coupons/{couponId}/claim" "Header: Authorization=consumer token; Path couponId=60003" "owned coupons exclude unclaimed coupon; available includes it; claim creates user coupon" "Coupon listing and claim persistence pass"
        }
        '^settlement-service\.SettlementServiceApiTests\.paymentApisCreatePaymentAndExposePaymentQueries$' {
            return New-TestCaseMetadata "Payment APIs" "User pays order and reads payment records" "POST; GET; GET" "/api/orders/{orderId}/pay; /api/orders/{orderId}/payments; /api/payments/{paymentId}" "Header: Authorization=consumer token; Body: {payMethod:WECHAT}" "pay code=200; order pending_accept; payment status=SUCCESS; queries return payment" "Payment record is created after order-service mark-paid succeeds"
        }
        '^settlement-service\.SettlementServiceApiTests\.healthEndpointExposesVersionAndDatabaseStatus$' {
            return New-TestCaseMetadata "Settlement health API" "Health endpoint exposes version and database status" "GET" "/api/health" "No auth required" "code=200; application=settlement-service; version present; databaseStatus=UP" "Health response includes readiness information"
        }
        '^settlement-service\.SettlementCouponInternalApiTests\.lockCouponMovesUnusedCouponToLocked$' {
            return New-TestCaseMetadata "Coupon lock internal API" "Lock unused coupon for checkout" "POST" "/internal/coupon-locks" "Body: {requestId:coupon-lock-1, userId:10001, couponId:60001, orderId:70001, orderAmount:50.00}" "code=200; locked=true; discount=10.00; status=locked" "Coupon lock state and discount are returned"
        }
        '^settlement-service\.SettlementCouponInternalApiTests\.lockCouponRejectsThresholdMismatch$' {
            return New-TestCaseMetadata "Coupon lock internal API" "Reject coupon lock when order amount is below threshold" "POST" "/internal/coupon-locks" "Body: {requestId:coupon-lock-2, userId:10001, couponId:60002, orderId:70002, orderAmount:50.00}" "code=400" "Threshold validation rejects lock"
        }
        '^settlement-service\.SettlementCouponInternalApiTests\.releaseCouponMovesLockedCouponBackToUnused$' {
            return New-TestCaseMetadata "Coupon release internal API" "Release locked coupon when order is cancelled or checkout rolls back" "POST" "/internal/coupon-locks/{orderId}/release" "Path orderId=70003" "code=200; message=release success" "Locked user coupon moves back to unused"
        }
        '^settlement-service\.SettlementPaymentServiceTests\.mockPaySuccessRequiresOrderToMoveToPendingAccept$' {
            return New-TestCaseMetadata "SettlementPaymentService.pay" "Payment fails when order-service confirmation does not move order to pending_accept" "SERVICE" "service:settlement-service/SettlementPaymentService.pay" "Call pay(10001, 70001, ALIPAY) while markPaid returns pending_payment" "BusinessException; no payment record inserted" "Payment persistence waits for confirmed order state"
        }
        '^settlement-service\.SettlementPaymentServiceTests\.mockPaySuccessRecordsPaymentWhenOrderIsConfirmed$' {
            return New-TestCaseMetadata "SettlementPaymentService.pay" "Payment succeeds when order-service confirms pending_accept" "SERVICE" "service:settlement-service/SettlementPaymentService.pay" "Call pay(10001, 70001, ALIPAY) while markPaid returns pending_accept" "Payment record status=SUCCESS; payTime present" "Successful mock payment records payment row"
        }
    }

    return New-TestCaseMetadata $classShort "$($Case.DisplayName)" "JUnit" "test:$service/$classShort#$method" "Execute test method with configured fixtures and mocks" "All assertions in the test method pass" "JUnit assertions and thrown-exception expectations pass"
}

function Get-TestCaseReportRow {
    param(
        [object]$Case,
        [string]$LogPath
    )

    $meta = Get-TestCaseMetadata $Case
    $duration = if ($null -ne $Case.DurationMs) { [math]::Round([double]$Case.DurationMs, 2) } else { 0 }
    $status = if ([string]::IsNullOrWhiteSpace($Case.Status)) { "UNKNOWN" } else { $Case.Status }
    $message = if ([string]::IsNullOrWhiteSpace($Case.Message)) { "" } else { $Case.Message }
    $actualResult = "$status in $duration ms"
    $actualAssertions = if ($status -eq "PASSED") {
        "$($meta.Assertions); JUnit result passed"
    } elseif (-not [string]::IsNullOrWhiteSpace($message)) {
        "$($meta.Assertions); failure=$message"
    } else {
        "$($meta.Assertions); see log"
    }
    $reason = if ($status -eq "PASSED") {
        "None"
    } elseif (-not [string]::IsNullOrWhiteSpace($message)) {
        $message
    } else {
        "See log file"
    }

    [pscustomobject]@{
        InterfaceName = $meta.InterfaceName
        Scenario = $meta.Scenario
        Method = $meta.Method
        Url = $meta.Url
        Request = $meta.Request
        Expected = $meta.Expected
        Actual = $actualResult
        Assertions = $actualAssertions
        Conclusion = $status
        Reason = $reason
        Evidence = $LogPath
    }
}
