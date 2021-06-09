<%
    ui.decorateWith("appui", "standardEmrPage")

    def htmlSafeId = { extension ->
        "${ extension.id.replace(".", "-") }-app"
    }
%>
<script>
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.message("Find Visit")}"}
    ];
</script>

<h2>
    ${ ui.message(heading) }
</h2>

<div class="row">
    <div class="col-12">
        <div id="patient-search-results"></div>
    </div>
</div>
