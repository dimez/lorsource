<%--
  ~ Copyright 1998-2022 Linux.org.ru
  ~    Licensed under the Apache License, Version 2.0 (the "License");
  ~    you may not use this file except in compliance with the License.
  ~    You may obtain a copy of the License at
  ~
  ~        http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~    Unless required by applicable law or agreed to in writing, software
  ~    distributed under the License is distributed on an "AS IS" BASIS,
  ~    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~    See the License for the specific language governing permissions and
  ~    limitations under the License.
  --%>
<%@ tag pageEncoding="UTF-8" %>
<%@ attribute name="reactions" required="true" type="ru.org.linux.reaction.PreparedReactions" %>
<%@ attribute name="all" required="false" type="java.lang.Boolean" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="lor" %>

<c:if test="${reactionsEnabled}">
  <c:if test="${all || not reactions.emptyMap}">
    <div class="reactions">
      <c:forEach var="r" items="${reactions.map}">
        <c:if test="${all || r.value.count > 0}">
          <c:set var="title">
            <c:forEach var="user" items="${r.value.topUsers}">${user.nick} </c:forEach>

            <c:if test="${r.value.hasMore}">...</c:if>
          </c:set>

          <c:set var="clicked">
            <c:if test="${r.value.clicked}">btn-primary</c:if>
          </c:set>

          <div class="reaction ${clicked}" title="${title}">
            <c:out value="${r.key}" escapeXml="true"/> ${r.value.count}
          </div>
        </c:if>
      </c:forEach>
    </div>
  </c:if>
</c:if>


