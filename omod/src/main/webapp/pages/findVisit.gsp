<%
    ui.decorateWith("appui", "standardEmrPage")
    ui.includeJavascript("coreapps", "visit/jquery.dataTables.js")
    ui.includeJavascript("coreapps", "visit/filterTable.js")
    ui.includeCss("coreapps", "visit/visits.css")

    def timeFormate = new java.text.SimpleDateFormat("hh:mm:ss")
%>
<script>
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("Find Visit")}"}
    ];
</script>

<p id="filter-tags" class="filters">
${ui.message("Filters")}
<% visitTypesWithAttr.each { type, attr -> %>
<span class="filter disabled" value="${type}"/>
<script type="text/javascript">
    jq(document).ready(function () {
        if ('${attr.color}' != null) {
            jq("#visittype-tag-${type}.tag").css("background",'${attr.color}');
        }
    })
</script>
<span id="visittype-tag-${type}" class="tag ${attr.shortName}" style="cursor:pointer;" >
    ${ui.format(attr.name)}
</span>
</span>
<% } %>
</p>

<table class="table table-sm table-responsive-sm table-responsive-md table-responsive-lg table-responsive-xl" id="active-visits" width="100%" border="1" cellspacing="0" cellpadding="2">
    <thead>
    <tr>
        <th>${ ui.message("coreapps.person.name") }</th>
        <th>${ ui.message("Date Created") }</th>
        <th>${ ui.message("Time Created") }</th>
        <th>${ ui.message("Status") }</th>
        <th>${ ui.message("coreapps.retrospectiveCheckin.visitType.label") }</th>
        <th>SMART Launch</th>
    </tr>
    </thead>
    <tbody>
    <% if (visitSummaries == null || (visitSummaries !=null && visitSummaries.size() == 0) ) { %>
    <tr>
        <td colspan="4">${ ui.message("coreapps.none") }</td>
    </tr>
    <% } %>
    <% visitSummaries.each { v ->
        def url = "/${ contextPath }/${afterSelectedUrl}".replace("{{visitId}}", v.uuid)
    %>
    <tr id="visit-${ v.id }">
        <td>${ ui.encodeHtmlContent(ui.format(v.patient)) }</td>
        <td>${ ui.encodeHtmlContent(ui.formatDatePretty(v.startDatetime)) }</td>
        <td>${ ui.encodeHtmlContent(ui.format(timeFormate.format(v.startDatetime))) }</td>
        <td>
            <% if (v.stopDatetime == null || new Date().before(v.stopDatetime)) { %> Active <% } else { %> Inactive <% } %>
        </td>
        <td>
            <% if (v.visitType) { %>
            <span style="display:none">${v.visitType.id}</span>
            <span id="visittype-tag-${ visitTypesWithAttr[v.visitType.id].shortName }" style="background: ${ visitTypesWithAttr[v.visitType.id].color }" class="tag" >
                ${ui.format(v.visitType)}
            </span>
            <br/>
            <% } %>
        </td>
        <td>
            <a href= ${url}>Click Here</a>
        </td>
    </tr>
    <% } %>
    </tbody>
</table>

<% if (visitSummaries !=null && visitSummaries.size() > 0) { %>
${ ui.includeFragment("uicommons", "widget/dataTable", [ object: "#active-visits",
                                                         options: [
                                                                 bFilter: true,
                                                                 bJQueryUI: true,
                                                                 bLengthChange: false,
                                                                 iDisplayLength: 10,
                                                                 sPaginationType: '\"full_numbers\"',
                                                                 bSort: false,
                                                                 sDom: '\'ft<\"fg-toolbar ui-toolbar ui-corner-bl ui-corner-br ui-helper-clearfix datatables-info-and-pg \"ip>\''
                                                         ]
]) }
<% } %>
