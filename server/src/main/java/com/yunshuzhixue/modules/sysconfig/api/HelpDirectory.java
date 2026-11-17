package com.yunshuzhixue.modules.sysconfig.api;

import java.util.List;

/**
 * 帮助中心内容（需求 5.11、7.7）。
 *
 * <p>常见问题与客服联系方式由 M03 维护，M17 通过本接口读取，不直接访问 faq 与 sys_config。</p>
 */
public interface HelpDirectory {

    /** 常见问题，按排序号升序 */
    List<Faq> faqs();

    /** 客服联系方式，三项均可为空 */
    Contact contact();

    record Faq(Long id, String question, String answer) {
    }

    record Contact(String wechatQrcodeUrl, String email, String phone) {
    }
}
