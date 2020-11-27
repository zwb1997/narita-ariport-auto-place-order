package work.service.orderservice.autoplaceorder;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import work.constants.BaseParameters;
import work.model.GoodModel;
import work.model.HttpClientUtilModel;
import work.model.RequireInfo;
import work.util.HttpClientUtil;
import work.util.PageUtil;

/**
 * @author xxx
 * 
 */
public class AutoPlaceOrderService {

        private static final Logger LOG = LoggerFactory.getLogger(AutoPlaceOrderService.class);

        private RequireInfo requireInfo;

        private GoodModel goodModel;

        private HttpClientUtilModel clientUtilModel;

        private HttpClientContext currentContext;

        private Map<String, String> cookieParamsMap;

        // need dynamic replaced with '?' when order place one more good
        private static final String GOOD_LIST_FORM_PARAM_SCD_NAME = "";
        // need dynamic replaced with '?' when order place one more good
        private static final String GOOD_LIST_FORM_PARAM_PRICE_NAME = "";
        // need dynamic replaced with '?' when order place one more good
        private static final String GOOD_LIST_FORM_PARAM_WARIBIKITANKA_NAME = "";
        // need dynamic replaced with '?' when order place one more good
        private static final String GOOD_LIST_FORM_PARAM_DDLNUM_NAME = "";

        public AutoPlaceOrderService(RequireInfo requireInfo, GoodModel goodModel, HttpClientUtilModel clientUtilModel,
                        HttpClientContext curreContext) {
                this.requireInfo = requireInfo;
                this.goodModel = goodModel;
                this.clientUtilModel = clientUtilModel;
                this.currentContext = curreContext;
                this.cookieParamsMap = new LinkedHashMap<>();
        }

        // params sCD
        public void addGoodAction(UrlEncodedFormEntity formEntity) throws Exception {
                LOG.info("begin add good...");
                if (ObjectUtils.isEmpty(formEntity)) {
                        LOG.error("addGoodAction , params formEntity is empty");
                        throw new Exception("addGoodActionparams formEntity is empty");
                }
                long s1 = System.nanoTime();
                List<NameValuePair> headers = HttpClientUtil.createRequestHeader(clientUtilModel.getReferer(),
                                currentContext);
                String uri = BaseParameters.GOOD_DETAIL_INFO + "?sCD=" + goodModel.getGoodId();
                HttpPost post = new HttpPost(uri);
                post.setEntity(formEntity);
                // use current cookie to add good to shopping trolley
                HttpClientUtil.defaultRequest(headers, post, currentContext, false, false, null);

                formEntity = null;
                LOG.info("add good end");

                List<NameValuePair> trolleyHeaders = HttpClientUtil.createRequestHeader(clientUtilModel.getReferer(),
                                currentContext);
                // request to shopping trolley
                HttpGet visitTrolley = new HttpGet(BaseParameters.TAKE_ORDER_ACTION_URI);
                HttpClientUtilModel visitTrolleyModel = HttpClientUtil.defaultRequest(trolleyHeaders, visitTrolley,
                                currentContext, true, false, null);
                // need stroage 'JAT-EC-Cart'
                // storageCookieParams();
                // extract params from visitTrolleyModel
                Document doc = Jsoup.parse(visitTrolleyModel.getHtml());

                List<NameValuePair> confirmTernimalAndDateFromParams = new ArrayList<>(20);

                UrlEncodedFormEntity confirnTernimalAndDateFormEntity = new UrlEncodedFormEntity(
                                confirmTernimalAndDateFromParams, Charset.forName("utf-8"));

                HttpPost confirnTernimalAndDatePost = new HttpPost(BaseParameters.TAKE_ORDER_ACTION_URI);

                confirnTernimalAndDatePost.setEntity(confirnTernimalAndDateFormEntity);

                List<NameValuePair> confirnTernimalAndDateHeaders = HttpClientUtil
                                .createRequestHeader(BaseParameters.TAKE_ORDER_ACTION_URI, currentContext);
                HttpClientUtilModel confirnTernimalAndDateModel = HttpClientUtil.defaultRequest(
                                confirnTernimalAndDateHeaders, confirnTernimalAndDatePost, currentContext, true, false,
                                null);

                LOG.info("confirm terminal and department date end");
                subAndReplaceParams(confirnTernimalAndDateModel.getHtml(), confirmTernimalAndDateFromParams,
                                Arrays.asList(""));

                confirmTernimalAndDateFromParams.remove(0);
                confirmTernimalAndDateFromParams.remove(confirmTernimalAndDateFromParams.size() - 1);

                List<NameValuePair> addGoodActionHeaders = HttpClientUtil
                                .createRequestHeader(BaseParameters.TAKE_ORDER_ACTION_URI, currentContext);

                HttpPost addGoodActionPost = new HttpPost(BaseParameters.TAKE_ORDER_ACTION_URI);
                addGoodActionPost.setEntity(
                                new UrlEncodedFormEntity(confirmTernimalAndDateFromParams, Charset.forName("utf-8")));

                HttpClientUtilModel addGoodActionModel = HttpClientUtil.defaultRequest(addGoodActionHeaders,
                                addGoodActionPost, currentContext, true, false, null);

                confirmTernimalAndDateFromParams = null;
                LOG.info("submit terminal and department date end");
                // next login

                String loginUrl = BaseParameters.LOGIN_URL + "?btnNm=LBtnGo";

                List<NameValuePair> loginHeaders = HttpClientUtil.createRequestHeader(loginUrl, currentContext);

                List<NameValuePair> loginFormParams = new ArrayList<>(15);
                Document loginPageDoc = Jsoup.parse(addGoodActionModel.getHtml());

                UrlEncodedFormEntity logiEntity = new UrlEncodedFormEntity(loginFormParams, Charset.forName("utf-8"));
                loginFormParams = null;
                HttpPost loginPost = new HttpPost(loginUrl);
                loginPost.setEntity(logiEntity);
                // logined ,get confirm airline info page
                HttpClientUtilModel afterLoginInModel = HttpClientUtil.defaultRequest(loginHeaders, loginPost,
                                currentContext, true, false, null);
                LOG.info("after login end");

                HttpClientUtil.addParamsIntoCookie(currentContext,
                                new BasicClientCookie("JAT-EC-Cart", cookieParamsMap.get("JAT-EC-Cart")));
                Document confirmAirPosDoc = Jsoup.parse(afterLoginInModel.getHtml());
                List<NameValuePair> confirmAirInfoPostParams = new ArrayList<>(30);

                goConfirmAirInfo(s1, confirmAirInfoPostParams);
        }

        /**
         * storage cookies
         */
        private void storageCookieParams() {
                CookieStore cookieStore = currentContext.getCookieStore();
                List<Cookie> cookies = cookieStore.getCookies();
                for (Cookie c : cookies) {
                        cookieParamsMap.put(c.getName(), c.getValue());
                }
        }

        /**
         * confirm airport info
         * 
         * @param confirmAirFormEntity
         */
        private void goConfirmAirInfo(long s1, List<NameValuePair> confirmAirParams) throws Exception {
                LOG.info("begin confirm airPort info");

                List<NameValuePair> confirmAirPortInfoHeaders = HttpClientUtil
                                .createRequestHeader(BaseParameters.BOARDING_INFO_INPUT_URI, currentContext);

                HttpPost confirmAirPortInfoPost = new HttpPost(BaseParameters.BOARDING_INFO_INPUT_URI);

                confirmAirPortInfoPost.setEntity(new UrlEncodedFormEntity(confirmAirParams, Charset.forName("utf-8")));

                HttpClientUtilModel afterConfirmAirportModel = HttpClientUtil.defaultRequest(confirmAirPortInfoHeaders,
                                confirmAirPortInfoPost, currentContext, true, false, null);

                LOG.info("after confirm air line info end");
                // reuse the paramsList
                confirmAirParams.remove(0);
                confirmAirParams.remove(confirmAirParams.size() - 1);

                HttpPost submitAirPortPost = new HttpPost(BaseParameters.BOARDING_INFO_INPUT_URI);
                submitAirPortPost.setEntity(new UrlEncodedFormEntity(confirmAirParams, Charset.forName("utf-8")));
                confirmAirParams = null;
                HttpClientUtilModel afterSubmitAirPortModel = HttpClientUtil.defaultRequest(confirmAirPortInfoHeaders,
                                submitAirPortPost, currentContext, true, false, null);
                // end
                LOG.info("submit confirm air line info end");
                // then go to select payment page
                Document afterSubmitAirPortDoc = Jsoup.parse(afterSubmitAirPortModel.getHtml());
                List<NameValuePair> beforePaymentFormParams = new ArrayList<>(20);

                HttpPost beforePaymentPost = new HttpPost(BaseParameters.BOARDING_INFO_CHECK_URI);
                beforePaymentPost
                                .setEntity(new UrlEncodedFormEntity(beforePaymentFormParams, Charset.forName("utf-8")));
                beforePaymentFormParams = null;
                List<NameValuePair> beforePaymentHeaders = HttpClientUtil
                                .createRequestHeader(BaseParameters.BOARDING_INFO_CHECK_URI, currentContext);

                HttpClientUtilModel beforePaymentModel = HttpClientUtil.defaultRequest(beforePaymentHeaders,
                                beforePaymentPost, currentContext, true, false, null);
                LOG.info("before payment end");
                // end

                // then choose reserve
                Document payselectDoc = Jsoup.parse(beforePaymentModel.getHtml());
                List<NameValuePair> payselectFormParams = new ArrayList<>(27);

                List<NameValuePair> payselectHeaders = HttpClientUtil.createRequestHeader(BaseParameters.PAY_SELECT_URI,
                                currentContext);

                HttpPost payselectPost = new HttpPost(BaseParameters.PAY_SELECT_URI);

                payselectPost.setEntity(new UrlEncodedFormEntity(payselectFormParams, Charset.forName("utf-8")));

                HttpClientUtilModel payselectModel = HttpClientUtil.defaultRequest(payselectHeaders, payselectPost,
                                currentContext, true, false, null);

                payselectFormParams.remove(0);
                // ....

                HttpPost paymentConfirmPost = new HttpPost(BaseParameters.PAY_SELECT_URI);

                paymentConfirmPost.setEntity(new UrlEncodedFormEntity(payselectFormParams, Charset.forName("utf-8")));

                payselectFormParams = null;

                HttpClientUtilModel afterPaymentModel = HttpClientUtil.defaultRequest(payselectHeaders,
                                paymentConfirmPost, currentContext, true, false, null);
                LOG.info("after payment end");

                Document afterPaymentPage = Jsoup.parse(afterPaymentModel.getHtml());

                List<NameValuePair> finalConfirmFormParams = new ArrayList<>(22);
                // ...

                List<NameValuePair> finalConfirmHeaders = HttpClientUtil
                                .createRequestHeader(BaseParameters.FINAL_CHECK_URI, currentContext);

                HttpPost finalConfirmPost = new HttpPost(BaseParameters.FINAL_CHECK_URI);
                finalConfirmPost.setEntity(new UrlEncodedFormEntity(finalConfirmFormParams, Charset.forName("utf-8")));
                finalConfirmFormParams = null;
                HttpClientUtilModel finalConfirmModel = HttpClientUtil.defaultRequest(finalConfirmHeaders,
                                finalConfirmPost, currentContext, true, false, null);

                LOG.info("after final confirm end");

                LOG.info(" use time >> {}", (System.nanoTime() - s1));

                Document finalConfirmDoc = Jsoup.parse(finalConfirmModel.getHtml());

                String completeText = PageUtil.getTextWithClassName(finalConfirmDoc,
                                BaseParameters.COMPLETE_TEXT_CLASS_NAME);
                LOG.info("success, text >>\"{}\"", completeText);
        }

        /**
         * 
         * @param html
         * @param list
         * @param needReplacedParams
         * @throws Exception
         */
        private void subAndReplaceParams(String html, List<NameValuePair> list, List<String> needReplacedParams)
                        throws Exception {
                if (StringUtils.isBlank(html) || CollectionUtils.isEmpty(needReplacedParams)) {
                        throw new Exception("require params is empty");
                }

                String[] newArr = html.substring(html.lastIndexOf("\n")).trim().split("\\|");

                if (newArr.length < 3) {
                        throw new Exception("the length is too short after sub the html");
                }

                int size = list.size();

                int newArrLength = newArr.length;

                for (int i = 0; i < size; i++) {
                        NameValuePair np = list.get(i);
                        if (needReplacedParams.contains(np.getName())) {
                                try {
                                        for (int j = 0; j < newArrLength; j++) {
                                                if (newArr[j].equals(np.getName())) {
                                                        list.set(i, new BasicNameValuePair(np.getName(),
                                                                        newArr[j + 1]));
                                                        break;
                                                }
                                        }
                                } catch (Exception e) {
                                        LOG.error(" pair name :{} could not find", np.getName());
                                        throw new Exception("pair could not find", e);
                                }

                        }
                }
        }
}
