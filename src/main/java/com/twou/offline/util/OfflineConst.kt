package com.twou.offline.util

object OfflineConst {

    const val IS_PREPARED = false

    val OFFLINE_VIDEO_SCRIPT = """
        <script>
            function getPos(el) {
                for (var lx=0, ly=0; el != null; lx += el.offsetLeft, ly += el.offsetTop, el = el.offsetParent);
                return {x: lx,y: ly};
            }
             
            function getVideoPositionAndNotify(element) {
                var src = element.getAttribute("src");
                var id = element.getAttribute("id");
                var subtitleUrl = element.getAttribute("subtitle_url");
                var pos = getPos(element);
                
                console.log("" + src + " " + subtitleUrl + " " + pos.x + " " + pos.y);
                javascript:window.offline.onProcessVideoPlayer(src, subtitleUrl, id, pos.x, pos.y);
            };
            
            function findAllVideoPlayers() {
                var players = document.getElementsByClassName("offline-video-player");
                for (var i = 0; i < players.length; i++) {
                    getVideoPositionAndNotify(players[i]);
                }
            };
            
            window.addEventListener('load', function () {
                window.addEventListener('resize', function(e) {
                    findAllVideoPlayers();  
                });
                
                findAllVideoPlayers(); 
            });
            
        </script>    
    """.trimIndent()

    const val PRINT_HTML =
        "javascript:window.android.print(document.getElementsByTagName('html')[0].innerHTML, document.location.href);"

    const val PROGRESS_CSS = """
        <style>
            .offline-video-privacy-error{
              position: relative;
              width: 50px;
              height: 50px;
              top: 50%;
              left: 50%;
              transform: translate(-50%,-50%);
            }
            
            .offline-video-preview-loader{
              position: relative;
              width: 100px;
              height: 100px;
              top: 50%;
              left: 50%;
              transform: translate(-50%,-50%);
            }
            
            .offline-video-preview-circular{
              animation: rotate 2s linear infinite;
              height: 100px;
              position: relative;
              width: 100px;
            }
            
            .offline-video-preview-path {
              stroke-dasharray: 1,200;
              stroke-dashoffset: 0;
              stroke:#B6463A;
              animation:
               dash 1.5s ease-in-out infinite,
               color 6s ease-in-out infinite;
              stroke-linecap: round;
            }
            
            @keyframes rotate{
             100%{
              transform: rotate(360deg);
             }
            }
            @keyframes dash{
             0%{
              stroke-dasharray: 1,200;
              stroke-dashoffset: 0;
             }
             50%{
              stroke-dasharray: 89,200;
              stroke-dashoffset: -35;
             }
             100%{
              stroke-dasharray: 89,200;
              stroke-dashoffset: -124;
             }
            }
            @keyframes color{
              100%, 0%{
                stroke: #d62d20;
              }
              40%{
                stroke: #0057e7;
              }
              66%{
                stroke: #008744;
              }
              80%, 90%{
                stroke: #ffa700;
              }
            } 
        </style>
        """

    const val HTML_ERROR_OVERLAY = """
        <div class="offline-error-container">
            <div class="offline-error-overlay">
                <div>
                    <p>#MESSAGE#</p>
                </div>
            </div>
            #OUTER_HTML#
            <div class="offline-error-note">
                <p>#MESSAGE#</p>
            </div>
        </div>
    """

    const val HTML_ERROR_SCRIPT = """
        function updatePlaceholders() {
            var overlays = Array.from(document.getElementsByClassName("offline-error-overlay"));
            var notes = Array.from(document.getElementsByClassName("offline-error-note"));
            var tapOverlays = Array.from(document.getElementsByClassName("campus-document-overlay-container"));
            
            var isOnline = window.offline.isOnline();
            if (isOnline) {
                overlays.forEach(function (element) {
                    element.style.display = "none";
                });
                notes.forEach(function (element) {
                    element.style.display = "flex";
                });
            } else {
                overlays.forEach(function (element) {
                    element.style.display = "block";
                });
                notes.forEach(function (element) {
                    element.style.display = "none";
                });
                tapOverlays.forEach(function (element) {
                    element.style.display = "none";
                });
            }
        }

        window.addEventListener("load", function (event) {
            updatePlaceholders();
        });

        document.addEventListener('DOMContentLoaded', function (event) {
            updatePlaceholders();
        });
    """

    const val HTML_ERROR_CSS = """
        .offline-error-container {
             position: relative;
             display: block;
             overflow: hidden;
             min-height: 80px;
        }

        .offline-error-container iframe {
            height: calc(100vw / (16/9));
            border: 0;
        }

        .offline-error-container audio {
            width: 100%;
        }

        .offline-error-container .offline-error-note {
            display: flex;
            justify-content: center;
            text-align: center;
            border-radius: 10px;
            border: 2px solid #e5146fff;
            margin-top: 20px;
        }
        
        #page-mod-video-view .custom-notes .offline-error-note p,
        .offline-error-container .offline-error-note p {
            padding: 10px !important;
            margin: 0px !important;
            margin-bottom: 0px !important;
            margin-top: 0px !important;
        }

        .offline-error-overlay {
             position: absolute;
             background-color: #000000;
             width: 100%;
             height: 100%;
             top: 0px;
             left: 0px;
             z-index: 400;
             text-align: center;
        }

        .offline-error-overlay div {
             width: 100%;
             height: 100%;
             display: flex;
             align-items: center;
             justify-content: center;
        }

        .offline-error-overlay div p {
             color: white;
             font-size: 17px;
             font-family: system-ui;
             font-weight: 500;
             background-color: .black;
             padding: 15px 32px;
             margin: 0;
             border-radius: 10px;
             display: inline-block;
        }
    """
}