###############################################################################################

# **Version v.2.3.2| 29-Nov-2022**

###############################################################################################

### Code Changes:

1. Payhub2u bug fixed - authorization removed

###############################################################################################

# **Version v.2.3.1| 17-Oct-2022**

###############################################################################################

### Code Changes:

1. added new payment provider -payhub2u

ALTER TABLE symplified.payment_sp ADD channel varchar(100) NOT NULL;


##############################################################################################

# **Version v.2.3.0 | 02-Sept-2022**

###############################################################################################

### Code Changes:
Query Pending Transaction


##############################################################################################

# **Version v.2.2.18 | 21-July-2022**

###############################################################################################

### Code Changes:
Update Order Status Bug Fixed - FAILED UPDATE STATUS API UPDATE


##############################################################################################

# **Version v.2.2.17 | 21-July-2022**

###############################################################################################

### Code Changes:
Update Order Status Bug Fixed


##############################################################################################

# **Version v.2.2.16 | 14-July-2022**

###############################################################################################

### Code Changes:
Bug fixed get token for the go pay fast

##############################################################################################

# **Version v.2.2.15 | 30-June-2022**

###############################################################################################

### Code Changes:
Change Get Total Value

##############################################################################################

# **Version v.2.2.14 | 30-June-2022**

###############################################################################################

### Code Changes:
Bug Fixed For Callback Payment Service


##############################################################################################

# **Version v.2.2.13 | 29-June-2022**

###############################################################################################

### Code Changes:
Handle for group order make payment and callback


##############################################################################################

# **Version v.2.2.12 | 22-June-2022**

###############################################################################################

### Code Changes:
1. Production Bug Fixed
Application Properties _dev Updates : goPayFastPaymentUrl =https://ipguat.apps.net.pk/Ecommerce/api/Transaction/PostTransaction
                                      origin =dev-pk.symplified.ai

2. Application Properties _Prod Updates : goPayFastPaymentUrl =https://ipg1.apps.net.pk/Ecommerce/api/Transaction/PostTransaction
                                          origin =easydukan.co




##############################################################################################

# **Version v.2.2.10 / v.2.2.11| 22-June-2022**

###############################################################################################

### Code Changes:
1. Added New Endpoint for proxy setting


##############################################################################################

# **Version v.2.2.9| 21-June-2022**

###############################################################################################

### Code Changes:
1. Redirect from backend

##############################################################################################

# **Version v.2.2.8| 15-June-2022**

###############################################################################################

### Code Changes:

1. GO Pay Fast Added Validation

Application properties :  paymentRedirectUrl =https://payment.dev-pk.symplified.ai/payment-redirect?


ALTER TABLE symplified.payment_orders ADD hash varchar(500) NULL;
ALTER TABLE symplified.payment_orders ADD hashDate varchar(100) NULL;
ALTER TABLE symplified.payment_orders CHANGE Column1 paymentAmount decimal(15,2) NULL;

##############################################################################################

# **Version v.2.2.7| 14-June-2022**

###############################################################################################

### Code Changes:

1. Payment Service - Update Order Table

##############################################################################################

# **Version v.2.2.3 & 2.2.4 & v.2.2.5 & v.2.2.6 | 13-June-2022**

###############################################################################################

### Code Changes:

1. Update order-service Payment Status
2. Redirect Store url bug fixed
3. Change Http Method for payment redirect
4. Payment Page Bug Fixed

##############################################################################################

# **Version v.2.2.2| 10-June-2022**

###############################################################################################

### Code Changes:

1. Added Redirect Url For Go Pay Fast - For Redirect Url Bug Fixed

##############################################################################################

# **Version v.2.2.1| 10-June-2022**

###############################################################################################

### Code Changes:

1. Added Redirect Url For Go Pay Fast - For Make Payment

##############################################################################################

# **Version v.2.2.0| 09-June-2022**

###############################################################################################

### Code Changes:

1. Added Redirect Url For Go Pay Fast

##############################################################################################

# **Version v.2.1.5| 03-June-2022**

###############################################################################################

### Code Changes:

1. Testing

##############################################################################################

# **Version v.2.1.4| 02-June-2022**

###############################################################################################

### Code Changes:

1. Request Change

##############################################################################################

# **Version v.2.1.3| 02-June-2022**

###############################################################################################

### Code Changes:

1. Response Change for the new endpoint

##############################################################################################

# **Version v.2.1.2| 02-June-2022**

###############################################################################################

### Code Changes:

1. Added New Endpoint to request Go Pay Fast

##############################################################################################

# **Version 2.1.1**

##############################################################################################
Date:25-May-2022
Developer:Kumar

1. Added Token Generation For GoPayFast

##############################################################################################

# **Version 2.1.0**

##############################################################################################
Date:24-May-2022
Developer:Kumar

1. Generate Staging link for GoPayFast

Version 2.0.10
--------------------------------
Date:21-March-2022
Developer:Kumar

1. Remove Request To SenangPayment Link

Version 2.0.9
--------------------------------
Date:11-March-2022
Developer:Kumar

1. Field change- generate payment link hash -change store name to systemTransactionId

Version 2.0.8
--------------------------------
Date:07-Jan-2022
Developer:Kumar

1. Improved - Bug Fixed Make Payment

Version 2.0.7
--------------------------------
Date:07-Jan-2022
Developer:Kumar

1. Improved - MakePayment Grand Total handle by backend

Version 2.0.6
--------------------------------
Date:06-Dec-2021
Developer:Kumar

1. Payment status store in payment orders table

Version 2.0.5
--------------------------------
Date:06-Dec-2021
Developer:Kumar

1. Callback url update status to failed when payment declined

Version 2.0.2
--------------------------------
Date:07-Oct-2021
Developer:Kumar

1. Fixed bug for user authentication in (Session Filter Class)
2. Response for Senangpay Callback Return "OK" in html view

Version v.1.0-FINAL
--------------------------------
Date:22-March-2021
Developer:Taufik

Update to version v.1.0-FINAL afer internal demo on 19-March-2021