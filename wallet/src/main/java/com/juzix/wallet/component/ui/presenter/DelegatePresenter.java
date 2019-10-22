package com.juzix.wallet.component.ui.presenter;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.util.Log;

import com.juzhen.framework.network.ApiRequestBody;
import com.juzhen.framework.network.ApiResponse;
import com.juzhen.framework.network.ApiSingleObserver;
import com.juzhen.framework.util.NumberParserUtils;
import com.juzix.wallet.R;
import com.juzix.wallet.app.CustomObserver;
import com.juzix.wallet.app.CustomThrowable;
import com.juzix.wallet.component.ui.base.BasePresenter;
import com.juzix.wallet.component.ui.contract.DelegateContract;
import com.juzix.wallet.component.ui.dialog.DelegateSelectWalletDialogFragment;
import com.juzix.wallet.component.ui.dialog.InputWalletPasswordDialogFragment;
import com.juzix.wallet.component.ui.dialog.TransactionAuthorizationDialogFragment;
import com.juzix.wallet.component.ui.dialog.TransactionSignatureDialogFragment;
import com.juzix.wallet.engine.DelegateManager;
import com.juzix.wallet.engine.NodeManager;
import com.juzix.wallet.engine.ServerUtils;
import com.juzix.wallet.engine.WalletManager;
import com.juzix.wallet.engine.Web3jManager;
import com.juzix.wallet.entity.DelegateDetail;
import com.juzix.wallet.entity.DelegateHandle;
import com.juzix.wallet.entity.Transaction;
import com.juzix.wallet.entity.TransactionAuthorizationBaseData;
import com.juzix.wallet.entity.TransactionAuthorizationData;
import com.juzix.wallet.entity.TransactionStatus;
import com.juzix.wallet.entity.TransactionType;
import com.juzix.wallet.entity.Wallet;
import com.juzix.wallet.utils.BigDecimalUtil;
import com.juzix.wallet.utils.RxUtils;
import com.juzix.wallet.utils.ToastUtil;

import org.web3j.crypto.Credentials;
import org.web3j.platon.ContractAddress;
import org.web3j.platon.FunctionType;
import org.web3j.platon.StakingAmountType;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.GasProvider;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import retrofit2.Response;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class DelegatePresenter extends BasePresenter<DelegateContract.View> implements DelegateContract.Presenter {
    private Wallet mWallet;
    private DelegateDetail mDelegateDetail;

    private String feeAmount;
    private List<String> walletAddressList = new ArrayList<>();
    private BigInteger gas_Price; //调web3j获取gasprice
    private BigInteger gas_limit;
    private boolean isAll = false;//是否点击全部

    public DelegatePresenter(DelegateContract.View view) {
        super(view);
        mDelegateDetail = view.getDelegateDetailFromIntent();
        mWallet = getDefaultWallet(mDelegateDetail);
    }

    @Override
    public void showSelectWalletDialogFragment() {
        DelegateSelectWalletDialogFragment.newInstance(mWallet != null ? mWallet.getUuid() : "", false)
                .setOnItemClickListener(new DelegateSelectWalletDialogFragment.OnItemClickListener() {
                    @Override
                    public void onItemClick(Wallet wallet) {
                        if (isViewAttached()) {
                            mWallet = wallet;
                            if (mWallet != null) {
                                getView().showSelectedWalletInfo(mWallet);
                            }
                            if (mDelegateDetail != null && mWallet != null) {
                                checkIsCanDelegate(mWallet.getPrefixAddress(), mDelegateDetail.getNodeId()); //0.7.3修改
                            }
                        }
                    }
                })
                .show(currentActivity().getSupportFragmentManager(), "showSelectWalletDialog");

    }


    @Override
    public void showWalletInfo() {
        if (isViewAttached()) {
            if (mDelegateDetail != null) {
                getView().showNodeInfo(mDelegateDetail);
            }
            if (mWallet != null) {
                getView().showSelectedWalletInfo(mWallet);
            }
            if (mDelegateDetail != null && mWallet != null) {
                checkIsCanDelegate(mWallet.getPrefixAddress(), mDelegateDetail.getNodeId()); //0.7.3修改
            }
        }
    }

    @Override
    public String checkDelegateAmount(String delegateAmount) {
        double amount = NumberParserUtils.parseDouble(delegateAmount);
        //检查委托的数量
        String errMsg = null;
        if (TextUtils.isEmpty(delegateAmount)) {
            errMsg = string(R.string.transfer_amount_cannot_be_empty);
        } else if (amount < 10) {
            //按钮不可点击,并且下方提示
            getView().showTips(true);
            updateDelegateButtonState();
        } else {
            getView().showTips(false);
            updateDelegateButtonState();
        }
        return delegateAmount;

    }

    @Override
    public void updateDelegateButtonState() {
        if (isViewAttached()) {
            String withdrawAmount = getView().getDelegateAmount(); //获取输入的委托数量
            boolean isAmountValid = !TextUtils.isEmpty(withdrawAmount) && NumberParserUtils.parseDouble(withdrawAmount) >= 10;
            getView().setDelegateButtonState(isAmountValid);
        }

    }

    private Wallet getDefaultWallet(DelegateDetail delegateDetail) {
        if (delegateDetail != null && !TextUtils.isEmpty(delegateDetail.getWalletAddress())) {
            return WalletManager.getInstance().getWalletEntityByWalletAddress(delegateDetail.getWalletAddress());
        } else {
            return sortByCreateTime(sortByFreeAccount(WalletManager.getInstance().getWalletList())).get(0);
        }
    }

    /**
     * 检测是否可以委托
     */
    @Override
    public void checkIsCanDelegate(String walletAddress, String nodeAddress) {
        ServerUtils.getCommonApi().getIsDelegateInfo(ApiRequestBody.newBuilder()
                .put("addr", walletAddress)
                .put("nodeId", nodeAddress)
                .build())
                .compose(RxUtils.bindToLifecycle(getView()))
                .compose(RxUtils.getSingleSchedulerTransformer())
                .subscribe(new ApiSingleObserver<DelegateHandle>() {
                    @Override
                    public void onApiSuccess(DelegateHandle delegateHandle) {
                        if (isViewAttached()) {
                            if (null != delegateHandle) {
                                getView().showIsCanDelegate(delegateHandle);
                            }

                        }

                    }

                    @Override
                    public void onApiFailure(ApiResponse response) {

                    }
                });
    }

    @SuppressLint("CheckResult")
    @Override
    public void getGas() {
        DelegateManager.getInstance().getGasPrice()
                .compose(RxUtils.bindToLifecycle(getView()))
                .compose(RxUtils.getSingleSchedulerTransformer())
                .subscribe(new Consumer<BigInteger>() {
                    @Override
                    public void accept(BigInteger gasPrice) throws Exception {
                        if (isViewAttached()) {
                            gas_Price = gasPrice;
                            getView().showGas(gasPrice);
                        }
                    }
                });
    }

    //获取手续费
    @SuppressLint("CheckResult")
    @Override
    public void getGasPrice(StakingAmountType stakingAmountType) {

        String inputAmount = getView().getDelegateAmount();//输入的数量

        if (TextUtils.isEmpty(inputAmount) || TextUtils.equals(inputAmount, ".")) {
            getView().showGasPrice("0.00");
            return;
        } else {
            if (NumberParserUtils.parseDouble(inputAmount) < 0) {
                getView().showGasPrice("0.00");
                return;
            }
        }

        if (mDelegateDetail != null && !TextUtils.isEmpty(mDelegateDetail.getNodeId())) {
            if (!isAll) {
                Log.d("gasprovide", "============" + "表示不是点击的全部");
                org.web3j.platon.contracts.DelegateContract
                        .load(Web3jManager.getInstance().getWeb3j())
                        .getDelegateGasProvider(mDelegateDetail.getNodeId(), stakingAmountType, Convert.toVon(inputAmount, Convert.Unit.LAT).toBigInteger())
                        .subscribe(new Action1<GasProvider>() {
                            @Override
                            public void call(GasProvider gasProvider) {
                                gas_limit = gasProvider.getGasLimit();
                                Log.d("gaslimit", "========getFeeAmount========" + "limit ===" + gas_limit + "gasprice ======" + gas_Price);
                                BigDecimal mul = BigDecimalUtil.mul(gasProvider.getGasLimit().toString(), gas_Price.toString());
                                feeAmount = NumberParserUtils.getPrettyBalance(BigDecimalUtil.div(mul.toString(), "1E18"));
                                getView().showGasPrice(feeAmount);
                            }
                        });
            } else {
                isAll = false;
                Log.d("gasprovide", "============" + "1111111111111表示点击的全部");
            }
        }

    }


    //点击全部的时候，需要获取一次手续费
    public void getAllPrice(StakingAmountType stakingAmountType, String amount) {
        isAll = true;
        if (mDelegateDetail != null) {
            org.web3j.platon.contracts.DelegateContract
                    .load(Web3jManager.getInstance().getWeb3j())
                    .getDelegateGasProvider(mDelegateDetail.getNodeId(), stakingAmountType, new BigInteger(Convert.toVon(amount, Convert.Unit.LAT).toBigInteger().toString().replaceAll("0", "1")))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<GasProvider>() {
                        @Override
                        public void call(GasProvider gasProvider) {
                            gas_limit = gasProvider.getGasLimit();
                            Log.d("gaslimit", "========getAllPrice========" + "limit" + gas_limit + "gasprice ========" + gas_Price);
                            Log.d("gasprovide", "========getAllPrice======" + gasProvider.getGasLimit());
                            BigDecimal mul = BigDecimalUtil.mul(gasProvider.getGasLimit().toString(), gas_Price.toString());
                            feeAmount = NumberParserUtils.getPrettyBalance(BigDecimalUtil.div(mul.toString(), "1E18"));
                            getView().showAllGasPrice(feeAmount);
                        }
                    });
        }
    }

    private String checkDelegateParam(DelegateHandle delegateHandle) {

        BigDecimal usedAmount = BigDecimalUtil.add(getView().getDelegateAmount(), getView().getFeeAmount());
        BigDecimal balanceAmount = new BigDecimal(getView().getChooseBalance());
        boolean isBalanceNotEnough = balanceAmount.compareTo(usedAmount) < 0;

        if (isBalanceNotEnough) {
            return string(R.string.insufficient_balance_unable_to_delegate);
        }
        return delegateHandle.getMessageDesc(getContext());
    }

    @SuppressLint("CheckResult")
    @Override
    public void submitDelegate(StakingAmountType stakingAmountType) {

        if (mWallet != null && mDelegateDetail != null) {
            getIsDelegateInfo(mWallet.getPrefixAddress(), mDelegateDetail.getNodeId())
                    .compose(RxUtils.getSingleSchedulerTransformer())
                    .compose(bindToLifecycle())
                    .subscribe(new ApiSingleObserver<DelegateHandle>() {
                        @Override
                        public void onApiSuccess(DelegateHandle delegateHandle) {
                            if (isViewAttached()) {
                                String errMsg = checkDelegateParam(delegateHandle);
                                if (!TextUtils.isEmpty(errMsg)) {
                                    showLongToast(errMsg);
                                } else {
                                    if (mWallet.isObservedWallet()) {
                                        showTransactionAuthorizationDialogFragment(mDelegateDetail.getNodeId(), mDelegateDetail.getNodeName(), stakingAmountType, getView().getDelegateAmount(), mWallet.getPrefixAddress(), ContractAddress.DELEGATE_CONTRACT_ADDRESS, gas_limit.toString(10), gas_Price.toString(10));
                                    } else {
                                        showInputPasswordDialogFragment(getView().getDelegateAmount(), mDelegateDetail.getNodeId(), mDelegateDetail.getNodeName(), stakingAmountType);
                                    }
                                }
                            }
                        }

                        @Override
                        public void onApiFailure(ApiResponse response) {
                            if (isViewAttached()) {
                                if (!TextUtils.isEmpty(response.getErrMsg(getContext()))) {
                                    showLongToast(response.getErrMsg(getContext()));
                                } else {
                                    showLongToast(R.string.delegate_failed);
                                }
                            }
                        }
                    });
        }

    }

    private Single<Response<ApiResponse<DelegateHandle>>> getIsDelegateInfo(String walletAddress, String nodeAddress) {
        return ServerUtils.getCommonApi().getIsDelegateInfo(ApiRequestBody.newBuilder()
                .put("addr", walletAddress)
                .put("nodeId", nodeAddress)
                .build());
    }


    @SuppressLint("CheckResult")
    private void delegate(Credentials credentials, String inputAmount, String nodeId, String nodeName, StakingAmountType stakingAmountType) {
        //这里调用新的方法，传入GasProvider
        GasProvider gasProvider = new ContractGasProvider(gas_Price, gas_limit);
        DelegateManager.getInstance().delegate(credentials, ContractAddress.DELEGATE_CONTRACT_ADDRESS, inputAmount, nodeId, nodeName, feeAmount, String.valueOf(TransactionType.DELEGATE.getTxTypeValue()), stakingAmountType, gasProvider)
                .compose(RxUtils.getSchedulerTransformer())
                .compose(RxUtils.getLoadingTransformer(currentActivity()))
                .subscribe(new Consumer<Transaction>() {
                    @Override
                    public void accept(Transaction transaction) throws Exception {
                        if (isViewAttached()) {
                            getView().transactionSuccessInfo(transaction);
                        }

                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        if (isViewAttached()) {
                            showLongToast(R.string.delegate_failed);
                        }
                    }
                });

    }

    private void showInputPasswordDialogFragment(String inputAmount, String nodeAddress, String nodeName, StakingAmountType stakingAmountType) {
        InputWalletPasswordDialogFragment
                .newInstance(mWallet)
                .setOnWalletPasswordCorrectListener(new InputWalletPasswordDialogFragment.OnWalletPasswordCorrectListener() {
                    @Override
                    public void onWalletPasswordCorrect(Credentials credentials) {
                        delegate(credentials, inputAmount, nodeAddress, nodeName, stakingAmountType);
                    }
                })
                .show(currentActivity().getSupportFragmentManager(), "inputWalletPasssword");
    }

    private void showTransactionAuthorizationDialogFragment(String nodeId, String nodeName, StakingAmountType stakingAmountType, String transactionAmount, String from, String to, String gasLimit, String gasPrice) {

        Observable
                .fromCallable(new Callable<BigInteger>() {
                    @Override
                    public BigInteger call() throws Exception {
                        return Web3jManager.getInstance().getNonce(from);
                    }
                })
                .compose(RxUtils.getSchedulerTransformer())
                .compose(bindToLifecycle())
                .compose(RxUtils.getLoadingTransformer(currentActivity()))
                .subscribe(new CustomObserver<BigInteger>() {
                    @Override
                    public void accept(BigInteger nonce) {
                        if (isViewAttached()) {
                            TransactionAuthorizationData transactionAuthorizationData = new TransactionAuthorizationData(Arrays.asList(new TransactionAuthorizationBaseData.Builder(FunctionType.DELEGATE_FUNC_TYPE)
                                    .setAmount(BigDecimalUtil.mul(transactionAmount, "1E18").toPlainString())
                                    .setChainId(NodeManager.getInstance().getChainId())
                                    .setNonce(nonce.toString(10))
                                    .setFrom(from)
                                    .setTo(to)
                                    .setGasLimit(gasLimit)
                                    .setGasPrice(gasPrice)
                                    .setNodeId(nodeId)
                                    .setNodeName(nodeName)
                                    .setStakingAmountType(stakingAmountType.getValue())
                                    .build()), System.currentTimeMillis() / 1000);
                            TransactionAuthorizationDialogFragment.newInstance(transactionAuthorizationData)
                                    .setOnNextBtnClickListener(new TransactionAuthorizationDialogFragment.OnNextBtnClickListener() {
                                        @Override
                                        public void onNextBtnClick() {
                                            TransactionSignatureDialogFragment.newInstance(transactionAuthorizationData)
                                                    .setOnSendTransactionSucceedListener(new TransactionSignatureDialogFragment.OnSendTransactionSucceedListener() {
                                                        @Override
                                                        public void onSendTransactionSucceed(String hash) {
                                                            if (isViewAttached()) {
                                                                getView().transactionSuccessInfo(new Transaction.Builder()
                                                                        .from(from)
                                                                        .to(to)
                                                                        .timestamp(System.currentTimeMillis())
                                                                        .txType(String.valueOf(TransactionType.DELEGATE.getTxTypeValue()))
                                                                        .value(Convert.toVon(transactionAmount, Convert.Unit.LAT).toBigInteger().toString())
                                                                        .actualTxCost(Convert.toVon(feeAmount, Convert.Unit.LAT).toBigInteger().toString())
                                                                        .nodeName(nodeName)
                                                                        .nodeId(nodeId)
                                                                        .txReceiptStatus(TransactionStatus.PENDING.ordinal())
                                                                        .hash(hash)
                                                                        .build());
                                                            }
                                                        }
                                                    })
                                                    .show(currentActivity().getSupportFragmentManager(), TransactionSignatureDialogFragment.TAG);
                                        }
                                    })
                                    .show(currentActivity().getSupportFragmentManager(), "showTransactionAuthorizationDialog");
                        }
                    }

                    @Override
                    public void accept(Throwable throwable) {
                        super.accept(throwable);
                        if (isViewAttached()) {
                            if (!TextUtils.isEmpty(throwable.getMessage())) {
                                ToastUtil.showLongToast(currentActivity(), throwable.getMessage());
                            }
                        }
                    }
                });
    }

    private List<Wallet> sortByFreeAccount(List<Wallet> walletList) {
        Collections.sort(walletList, new Comparator<Wallet>() {
            @Override
            public int compare(Wallet o1, Wallet o2) {
                return Double.compare(NumberParserUtils.parseDouble(NumberParserUtils.getPrettyBalance(BigDecimalUtil.div(o2.getFreeBalance(), "1E18"))), NumberParserUtils.parseDouble(NumberParserUtils.getPrettyBalance(BigDecimalUtil.div(o1.getFreeBalance(), "1E18"))));
            }
        });

        return walletList;
    }

    private List<Wallet> sortByCreateTime(List<Wallet> list) {
        Collections.sort(list, new Comparator<Wallet>() {
            @Override
            public int compare(Wallet o1, Wallet o2) {
                return Long.compare(o1.getCreateTime(), o2.getCreateTime());
            }
        });
        return list;
    }
}
