/*
 * Copyright 1998-2021 Linux.org.ru
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package ru.org.linux.spring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import ru.org.linux.auth.AccessViolationException;
import ru.org.linux.auth.IPBlockDao;
import ru.org.linux.auth.IPBlockInfo;
import ru.org.linux.comment.CommentDao;
import ru.org.linux.comment.CommentPrepareService;
import ru.org.linux.comment.PreparedCommentsListItem;
import ru.org.linux.site.BadInputException;
import ru.org.linux.site.MessageNotFoundException;
import ru.org.linux.site.ScriptErrorException;
import ru.org.linux.site.Template;
import ru.org.linux.spring.dao.UserAgentDao;
import ru.org.linux.user.UserDao;
import ru.org.linux.util.StringUtil;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class SameIPController {
  private static final Pattern ipRE = Pattern.compile("^([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)(/\\d{1,2})?$");

  private final IPBlockDao ipBlockDao;

  private final UserDao userDao;

  private final UserAgentDao userAgentDao;
  private final CommentDao commentDao;
  private final CommentPrepareService commentPrepareService;

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  public SameIPController(IPBlockDao ipBlockDao, UserDao userDao, UserAgentDao userAgentDao, CommentDao commentDao,
                          CommentPrepareService commentPrepareService, DataSource ds) {
    this.ipBlockDao = ipBlockDao;
    this.userDao = userDao;
    this.userAgentDao = userAgentDao;
    this.commentDao = commentDao;
    this.commentPrepareService = commentPrepareService;
    jdbcTemplate = new JdbcTemplate(ds);
    namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
  }

  @RequestMapping("/sameip.jsp")
  public ModelAndView sameIP(
    HttpServletRequest request,
    @RequestParam(required = false) Integer msgid,
    @RequestParam(required = false) String ip,
    @RequestParam(required = false, name="ua") Integer userAgent
  ) throws Exception {
    Template tmpl = Template.getTemplate(request);

    if (!tmpl.isModeratorSession()) {
      throw new AccessViolationException("Not moderator");
    }

    String actualIp;

    ModelAndView mv = new ModelAndView("sameip");

    int mainMessageUseragent = 0;

    if (msgid != null) {
      SqlRowSet rs = jdbcTemplate.queryForRowSet(
              "SELECT postip, ua_id FROM topics WHERE id=?",
              msgid
      );

      if (!rs.next()) {
        rs = jdbcTemplate.queryForRowSet("SELECT postip, ua_id FROM comments WHERE id=?", msgid);
        if (!rs.next()) {
          throw new MessageNotFoundException(msgid);
        }
      }

      actualIp = rs.getString("postip");
      mainMessageUseragent = rs.getInt("ua_id");

      if (actualIp == null) {
        throw new ScriptErrorException("No IP data for #" + msgid);
      }
    } else {
      if (ip!=null) {
        Matcher matcher = ipRE.matcher(ip);

        if (!matcher.matches()) {
          throw new BadInputException("not ip");
        }

        actualIp = matcher.group(1);
      } else {
        actualIp = null;
      }
    }

    if (actualIp == null && userAgent == null) {
      throw new BadInputException("one of msgid/ip/useragent required");
    }

    int topicsLimit = tmpl.getProf().getTopics();
    List<TopicItem> topics = getTopics(ip, userAgent, topicsLimit);
    List<PreparedCommentsListItem> comments = commentPrepareService.prepareCommentsList(commentDao.getCommentsByUAIP(ip, userAgent, topicsLimit));

    mv.getModel().put("topics", topics);
    mv.getModel().put("hasMoreTopics", topics.size() == topicsLimit);
    mv.getModel().put("comments", comments);
    mv.getModel().put("hasMoreComments", comments.size() == topicsLimit);
    mv.getModel().put("topicsLimit", topicsLimit);

    if (actualIp != null) {
      mv.getModel().put("ip", actualIp);
      mv.getModel().put("ipMask", ip);
      boolean hasMask = hasMask(ip);
      if (!hasMask) {
        mv.getModel().put("ipMore", actualIp + "/24");
      }
      mv.getModel().put("hasMask", hasMask);
      mv.getModel().put("users", getUsers(actualIp, mainMessageUseragent));

      if (!hasMask) {
        IPBlockInfo blockInfo = ipBlockDao.getBlockInfo(actualIp);

        boolean allowPosting = false;
        boolean captchaRequired = true;

        if (blockInfo.isInitialized()) {
          mv.getModel().put("blockInfo", blockInfo);
          allowPosting = blockInfo.isAllowRegistredPosting();
          captchaRequired = blockInfo.isCaptchaRequired();

          if (blockInfo.getModerator() != 0) {
            mv.getModel().put("blockModerator", userDao.getUserCached(blockInfo.getModerator()));
          }
        }
        mv.addObject("allowPosting", allowPosting);
        mv.addObject("captchaRequired", captchaRequired);
      }
    }

    if (userAgent!=null) {
      mv.getModel().put("userAgent", userAgentDao.getUserAgentById(userAgent));
    }

    return mv;
  }

  private boolean hasMask(String ip) {
    return ip.contains("/");
  }

  private List<TopicItem> getTopics(@Nullable String ip, @Nullable Integer userAgent, int limit) {
    String ipQuery = ip!=null?"AND topics.postip <<= :ip::inet ":"";
    String userAgentQuery = userAgent!=null?"AND topics.ua_id=:userAgent ":"";

    Map<String, Object> params = new HashMap<>();

    params.put("ip", ip);
    params.put("userAgent", userAgent);
    params.put("limit", limit);

    return namedJdbcTemplate.query(
            "SELECT sections.name as ptitle, groups.title as gtitle, topics.title as title, topics.id as msgid, postdate, deleted " +
                    "FROM topics, groups, sections, users " +
                    "WHERE topics.groupid=groups.id " +
                    "AND sections.id=groups.section " +
                    "AND users.id=topics.userid " +
                    ipQuery +
                    userAgentQuery +
                    "AND postdate>CURRENT_TIMESTAMP-'3 days'::interval ORDER BY msgid DESC LIMIT :limit",
            params,
            (rs, rowNum) -> new TopicItem(rs)
    );
  }

  private List<UserItem> getUsers(String ip, final int uaId) {
    return jdbcTemplate.query(
            "SELECT MAX(c.postdate) AS lastdate, u.nick, c.ua_id, ua.name AS user_agent " +
                    "FROM comments c LEFT JOIN user_agents ua ON c.ua_id = ua.id " +
                    "JOIN users u ON c.userid = u.id " +
                    "WHERE c.postip=?::inet " +
                    "GROUP BY u.nick, c.ua_id, ua.name " +
                    "ORDER BY MAX(c.postdate) DESC, u.nick, ua.name",
            (rs, rowNum) -> new UserItem(rs, uaId),
            ip
    );
  }

  public static class TopicItem {
    private final String ptitle;
    private final String gtitle;
    private final int id;
    private final String title;
    private final Timestamp postdate;
    private final boolean deleted;

    private TopicItem(ResultSet rs) throws SQLException {
      ptitle = rs.getString("ptitle");
      gtitle = rs.getString("gtitle");
      id = rs.getInt("msgid");
      title = StringUtil.makeTitle(rs.getString("title"));
      postdate = rs.getTimestamp("postdate");

      deleted = rs.getBoolean("deleted");
    }

    public String getPtitle() {
      return ptitle;
    }

    public String getGtitle() {
      return gtitle;
    }

    public int getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public Timestamp getPostdate() {
      return postdate;
    }

    public boolean isDeleted() {
      return deleted;
    }
  }

  public static class UserItem {
    private final Timestamp lastdate;
    private final String nick;
    private final boolean sameUa;
    private final String userAgent;

    private UserItem(ResultSet rs, int uaId) throws SQLException {
      lastdate = rs.getTimestamp("lastdate");
      nick = rs.getString("nick");
      sameUa = uaId == rs.getInt("ua_id");
      userAgent = rs.getString("user_agent");
    }

    public Timestamp getLastdate() {
      return lastdate;
    }

    public String getNick() {
      return nick;
    }

    public boolean isSameUa() {
      return sameUa;
    }

    public String getUserAgent() {
      return userAgent;
    }
  }
}
