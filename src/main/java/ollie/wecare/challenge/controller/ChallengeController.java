package ollie.wecare.challenge.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ollie.wecare.challenge.dto.*;
import ollie.wecare.challenge.service.ChallengeService;
import ollie.wecare.common.base.BaseException;
import ollie.wecare.common.base.BaseResponse;
import ollie.wecare.user.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static ollie.wecare.common.base.BaseResponseStatus.SUCCESS;
import static ollie.wecare.common.constants.RequestURI.challenge;

@RestController
@RequestMapping(challenge)
@RequiredArgsConstructor
@Slf4j
public class ChallengeController {

    private final ChallengeService challengeService;
    private final AuthService authService;

    // 참여 중인 챌린지 목록 조회
    @GetMapping
    public BaseResponse<List<GetChallengesRes>> getMyChallenges() {
        return challengeService.getMyChallenges(authService.getUserIdx());
    }

    // [관리자] 관리자가 생성한 챌린지 목록 조회
    @GetMapping("/admin")
    public BaseResponse<List<GetChallengesAdminRes>> getMyChallengesAdmin() {
        return challengeService.getMyChallengesAdmin(authService.getUserIdx());
    }

    // [관리자] 챌린지 상세 조회
    @GetMapping("/admin/{challengeIdx}")
    public BaseResponse<List<GetChallengeAdminRes>> getMyChallengeAdmin(@PathVariable(value = "challengeIdx") Long challengeIdx) {
        return challengeService.getMyChallengeAdmin(authService.getUserIdx(), challengeIdx);
    }

    // 챌린지 인증코드 발급
    @PostMapping("/attendance/{challengeIdx}")
    public BaseResponse<GetAttendanceCodeReq> getAttendanceCode(@PathVariable(value = "challengeIdx") Long challengeIdx) {
        return new BaseResponse<>(challengeService.getAttendanceCode(challengeIdx));
    }

    // 챌린지 인증
    //TODO : PathVariable로 변경
    @PostMapping("/attendance")
    public BaseResponse<String> attendChallenge(@RequestBody AttendChallengeReq attendChallengeReq) {
        challengeService.attendChallenge(attendChallengeReq);
        return new BaseResponse<>(SUCCESS);
    }

    // 챌린지 상세 조회
    @GetMapping("/attendance/{challengeIdx}")
    public BaseResponse<ChallengeDetailResponse> getChallengeDetail(@PathVariable Long challengeIdx) {
        return challengeService.getChallengeDetail(authService.getUserIdx(), challengeIdx);
    }

    // 새로운 챌린지 참여
    @PostMapping("/participation")
    public BaseResponse<String> participateChallenge(@RequestBody PostChallengeReq postChallengeReq) {
        challengeService.participateChallenge(postChallengeReq);
        return new BaseResponse<>(SUCCESS);
    }

    // 챌린지 검색
    @GetMapping("/search")
    public BaseResponse<List<SearchChallengeRes>> getChallenges(@RequestParam(value = "searchWord", defaultValue = "", required = false) String searchWord) throws BaseException {
        return new BaseResponse<>(challengeService.getChallenges(searchWord));
    }

    // 챌린지 광고 조회
    @GetMapping("/ads")
    public BaseResponse<GetChallengeAdsRes> getChallengeAds() {
        return new BaseResponse<>(challengeService.getChallengeAds());
    }
}
