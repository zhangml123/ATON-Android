package com.juzix.wallet.component.ui.presenter;

import android.text.TextUtils;

import com.juzhen.framework.util.NumberParserUtils;
import com.juzix.wallet.R;
import com.juzix.wallet.app.FlowableSchedulersTransformer;
import com.juzix.wallet.app.SchedulersTransformer;
import com.juzix.wallet.component.ui.SortType;
import com.juzix.wallet.component.ui.base.BasePresenter;
import com.juzix.wallet.component.ui.contract.VoteContract;
import com.juzix.wallet.component.ui.view.SubmitVoteActivity;
import com.juzix.wallet.engine.CandidateManager;
import com.juzix.wallet.engine.IndividualWalletManager;
import com.juzix.wallet.engine.VoteManager;
import com.juzix.wallet.entity.CandidateEntity;
import com.juzix.wallet.entity.CandidateExtraEntity;
import com.juzix.wallet.entity.IndividualWalletEntity;
import com.juzix.wallet.utils.BigDecimalUtil;
import com.trello.rxlifecycle2.android.FragmentEvent;

import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

/**
 * @author matrixelement
 */
public class VotePresenter extends BasePresenter<VoteContract.View> implements VoteContract.Presenter {

    private final static String TAG = VotePresenter.class.getSimpleName();
    private final static int MAX_TICKET_POOL_SIZE = 51200;
    private final static int DEFAULT_VOTE_NUM = 100;
    private final static int DEFAULT_DEPOSIT_RANKING = 100;
    private static final int REFRESH_TIME = 5000;

    private List<CandidateEntity> mCandidateEntiyList = new ArrayList<>();
    private List<CandidateEntity> mVerifiersList = new ArrayList<>();
    private String mKeyword;
    private SortType mSortType = SortType.SORTED_BY_DEFAULT;
    private Disposable mTicketInfoDisposable;
    private Disposable mCandidateLsitDisposable;

    public VotePresenter(VoteContract.View view) {
        super(view);
    }

    @Override
    public void start() {
        getTicketInfo();
        getCandidateList();
    }

    @Override
    public void sort(SortType sortType) {
        this.mSortType = sortType;
        showCandidateList(mKeyword, sortType);
    }

    @Override
    public void search(String keyword) {
        this.mKeyword = keyword;
        showCandidateList(keyword, mSortType);
    }

    @Override
    public void voteTicket(CandidateEntity candidateEntity) {

        if (isViewAttached()) {

            ArrayList<IndividualWalletEntity> walletEntityList = IndividualWalletManager.getInstance().getWalletList();
            if (walletEntityList.isEmpty()) {
                showLongToast(R.string.voteTicketCreateWalletTips);
                return;
            }

            Flowable
                    .fromIterable(walletEntityList)
                    .map(new Function<IndividualWalletEntity, Double>() {

                        @Override
                        public Double apply(IndividualWalletEntity individualWalletEntity) throws Exception {
                            return individualWalletEntity.getBalance();
                        }
                    })
                    .reduce(new BiFunction<Double, Double, Double>() {
                        @Override
                        public Double apply(Double aDouble, Double aDouble2) throws Exception {
                            return BigDecimalUtil.add(aDouble, aDouble2);
                        }
                    })
                    .subscribe(new Consumer<Double>() {
                        @Override
                        public void accept(Double totalBalance) throws Exception {
                            if (totalBalance <= 0) {
                                showLongToast(R.string.voteTicketInsufficientBalanceTips);
                            } else {
                                SubmitVoteActivity.actionStart(currentActivity(), candidateEntity);
                            }
                        }
                    });
        }

    }

    private void getTicketInfo() {
        if (mTicketInfoDisposable != null && !mTicketInfoDisposable.isDisposed()) {
            mTicketInfoDisposable.dispose();
        }
        mTicketInfoDisposable = Single
                .zip(VoteManager.getInstance().getPoolRemainder(), VoteManager.getInstance().getTicketPrice(), new BiFunction<Long, String, String>() {
                    @Override
                    public String apply(Long poolRemainder, String ticketPrice) throws Exception {
                        return poolRemainder + ":" + ticketPrice;
                    }
                })
                .repeatWhen(new Function<Flowable<Object>, Publisher<?>>() {
                    @Override
                    public Publisher<?> apply(Flowable<Object> objectFlowable) throws Exception {
                        return objectFlowable.delay(REFRESH_TIME, TimeUnit.MILLISECONDS);
                    }
                })
                .compose(new FlowableSchedulersTransformer())
                .compose(bindUntilEvent(FragmentEvent.STOP))
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String result) throws Exception {
                        if (isViewAttached()) {
                            String poolRemainder = result.split(":", 2)[0];
                            String ticketPrice = result.split(":", 2)[1];
                            long votedNum = MAX_TICKET_POOL_SIZE - NumberParserUtils.parseLong(poolRemainder);
                            getView().setVotedInfo(MAX_TICKET_POOL_SIZE, votedNum, ticketPrice);
                        }
                    }
                });
    }

    private void getCandidateList() {
        if (mCandidateLsitDisposable != null && !mCandidateLsitDisposable.isDisposed()) {
            mCandidateLsitDisposable.dispose();
        }
        mCandidateLsitDisposable = CandidateManager
                .getInstance()
                .getCandidateList()
                .zipWith(CandidateManager.getInstance().getVerifiersList(), new BiFunction<List<CandidateEntity>, List<CandidateEntity>, List<CandidateEntity>>() {
                    @Override
                    public List<CandidateEntity> apply(List<CandidateEntity> candidateEntities, List<CandidateEntity> verifiersList) throws Exception {
                        mVerifiersList = verifiersList;
                        return candidateEntities;
                    }
                })
                .compose(bindUntilEvent(FragmentEvent.STOP))
                .compose(new SchedulersTransformer())
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        if (mCandidateEntiyList == null || mCandidateEntiyList.isEmpty()) {
                            showLoadingDialog();
                        }
                    }
                })
                .doFinally(new Action() {
                    @Override
                    public void run() throws Exception {
                        dismissLoadingDialogImmediately();
                    }
                })
                .repeatWhen(new Function<Flowable<Object>, Publisher<?>>() {
                    @Override
                    public Publisher<?> apply(Flowable<Object> objectFlowable) throws Exception {
                        return objectFlowable.delay(REFRESH_TIME, TimeUnit.MILLISECONDS);
                    }
                })
                .subscribe(new Consumer<List<CandidateEntity>>() {
                    @Override
                    public void accept(List<CandidateEntity> candidateEntityList) throws Exception {
                        if (isViewAttached()) {
                            mCandidateEntiyList = candidateEntityList;
                            showCandidateList(mKeyword, mSortType);
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {

                    }
                });
    }

    private List<CandidateEntity> getDefaultCandidateEntityList(List<CandidateEntity> candidateEntityList, List<CandidateEntity> verifiersList) {
        //全量提名节点
        List<CandidateEntity> allCandidateList = getAllCandidateList(candidateEntityList);
        //全量候选节点
        List<CandidateEntity> allAlternativeList = getAllReserveList(candidateEntityList);

        //不在池子中的验证节点
        List<CandidateEntity> absentVerifiersList = getAbsentVerifiersList(allCandidateList, allAlternativeList, verifiersList);
        //在池子中的验证节点
        List<CandidateEntity> inVerifiersList = verifiersList;
        //剔除掉验证节点的提名节点列表
        List<CandidateEntity> partCandidateList = getCandidateList(allCandidateList, verifiersList);
        //剔除掉验证节点的候选节点列表
        List<CandidateEntity> partReserveList = getReserveList(allAlternativeList, verifiersList);

        //排序顺序为：验证节点(在池子中)+提名节点+验证人节点(不在池子中)+候选节点
        inVerifiersList.removeAll(absentVerifiersList);
        inVerifiersList.addAll(partCandidateList);
        inVerifiersList.addAll(absentVerifiersList);
        inVerifiersList.addAll(partReserveList);

        return inVerifiersList;
    }

    private void showCandidateList(String keyWord, SortType sortType) {

        if (mCandidateEntiyList != null) {

            List<CandidateEntity> candidateEntities = getSearchResult(keyWord, mCandidateEntiyList);

            Collections.sort(candidateEntities, sortType.getComparator());

            if (sortType == SortType.SORTED_BY_DEFAULT) {
                candidateEntities = getDefaultCandidateEntityList(candidateEntities, mVerifiersList);
            }

            if (isViewAttached()) {
                if (!TextUtils.isEmpty(keyWord) && (candidateEntities == null || candidateEntities.isEmpty())) {
                    showLongToast(R.string.query_no_result);
                }
                getView().notifyDataSetChanged(candidateEntities);
            }
        }
    }

    /**
     * 获取全量的提名节点列表
     *
     * @param candidateEntityList
     * @return
     */
    private List<CandidateEntity> getAllCandidateList(List<CandidateEntity> candidateEntityList) {
        List<CandidateEntity> candidateList = new ArrayList<>();
        if (candidateEntityList == null || candidateEntityList.isEmpty()) {
            return candidateList;
        }
        CandidateEntity entity = null;
        for (int i = 0; i < candidateEntityList.size(); i++) {
            //剔除排名200后的节点
            entity = candidateEntityList.get(i);
            if (i < 2 * DEFAULT_DEPOSIT_RANKING) {
                entity.setStakedRanking(i + 1);
                if (i < DEFAULT_DEPOSIT_RANKING && entity.getVotedNum() >= DEFAULT_VOTE_NUM) {
                    entity.setStatus(CandidateEntity.CandidateStatus.STATUS_RESERVE);
                    candidateList.add(entity);
                }
            }
        }

        return candidateList;
    }

    /**
     * 获取全量的候选节点
     *
     * @param candidateEntityList
     * @return
     */
    private List<CandidateEntity> getAllReserveList(List<CandidateEntity> candidateEntityList) {
        List<CandidateEntity> reserveList = new ArrayList<>();
        if (candidateEntityList == null || candidateEntityList.isEmpty()) {
            return reserveList;
        }
        CandidateEntity entity = null;
        for (int i = 0; i < candidateEntityList.size(); i++) {
            //剔除排名200后的节点
            entity = candidateEntityList.get(i);
            if (i < 2 * DEFAULT_DEPOSIT_RANKING) {
                entity.setStakedRanking(i + 1);
                if (i >= DEFAULT_DEPOSIT_RANKING || entity.getVotedNum() < DEFAULT_VOTE_NUM) {
                    entity.setStatus(CandidateEntity.CandidateStatus.STATUS_RESERVE);
                    reserveList.add(entity);
                }
            }
        }
        return reserveList;
    }

    /**
     * 获取提名节点列表，剔除掉验证节点
     *
     * @param candidateList
     * @param verifyList
     * @return
     */
    private List<CandidateEntity> getCandidateList(List<CandidateEntity> candidateList, List<CandidateEntity> verifyList) {
        if (verifyList == null || verifyList.isEmpty() || candidateList == null || candidateList.isEmpty()) {
            return candidateList;
        }
        List<CandidateEntity> tempCandidateList = candidateList;

        for (CandidateEntity candidateEntity : candidateList) {
            if (verifyList.contains(candidateEntity)) {
                tempCandidateList.add(candidateEntity);
            }
        }
        candidateList.removeAll(tempCandidateList);
        return candidateList;
    }

    /**
     * 获取候选节点列表，剔除掉验证节点
     *
     * @param reserveList
     * @param verifyList
     * @return
     */
    private List<CandidateEntity> getReserveList(List<CandidateEntity> reserveList, List<CandidateEntity> verifyList) {

        if (verifyList == null || verifyList.isEmpty() || reserveList == null || reserveList.isEmpty()) {
            return reserveList;
        }
        List<CandidateEntity> tempReserveList = new ArrayList<>();

        for (CandidateEntity candidateEntity : reserveList) {
            if (verifyList.contains(candidateEntity)) {
                tempReserveList.add(candidateEntity);
            }
        }
        reserveList.removeAll(tempReserveList);

        return reserveList;
    }

    /**
     * 获取不在池子中的验证节点列表，要放在提名节点的后面
     *
     * @param candidateList
     * @param reserveList
     * @param verifyList
     * @return
     */
    private List<CandidateEntity> getAbsentVerifiersList(List<CandidateEntity> candidateList, List<CandidateEntity> reserveList, List<CandidateEntity> verifyList) {
        List<CandidateEntity> absentVerifiersList = new ArrayList<>();
        if (verifyList == null || verifyList.isEmpty()) {
            return absentVerifiersList;
        }
        for (CandidateEntity candidateEntity : verifyList) {
            if (candidateList.contains(candidateEntity) || reserveList.contains(candidateEntity)) {
                continue;
            }
            absentVerifiersList.add(candidateEntity);
        }
        return absentVerifiersList;
    }

    private List<CandidateEntity> getSearchResult(String keyWord, List<CandidateEntity> candidateEntityList) {
        if (TextUtils.isEmpty(keyWord)) {
            return candidateEntityList;
        } else {
            List<CandidateEntity> result = new ArrayList<>();
            for (CandidateEntity candidateEntity : mCandidateEntiyList) {
                CandidateExtraEntity candidateExtraEntity = candidateEntity.getCandidateExtraEntity();
                if (candidateExtraEntity != null) {
                    String nodeName = candidateExtraEntity.getNodeName();
                    if ((!TextUtils.isEmpty(nodeName)) && nodeName.toLowerCase().contains(mKeyword.toLowerCase())) {
                        result.add(candidateEntity);
                    }
                }
            }
            return result;
        }
    }

}
